package com.Atom2Universe.app.games.roguelike

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.roguelike.LexiconText.setLexiconText
import com.Atom2Universe.app.util.SystemBarsManager
import java.text.NumberFormat
import kotlin.math.abs

/** Separate character, paginated equipment and relic pages. */
class InventoryPanel(private val root: View, private val lexicon: LexiconPanel, private val onChanged: () -> Unit) {
    private enum class Page(val label: Int) {
        CHARACTER(R.string.inv_page_character), EQUIPMENT(R.string.inv_page_equipment), RELICS(R.string.inv_page_relics)
    }
    private var page = Page.CHARACTER
    private val ctx: Context = root.context
    private val density = ctx.resources.displayMetrics.density
    private val ink = 0xFFE5EAF2.toInt()
    private val muted = 0xFFADB9CD.toInt()
    private val green = 0xFF80D6A0.toInt()
    private val red = 0xFFFF9393.toInt()
    private val accent = 0xFFE8BF78.toInt()
    private var game: RoguelikeGame? = null
    private var slot: EquipSlot? = null
    private var type: ItemBase? = null
    private var weight: ArmorWeight? = null
    private var rarity = 0
    private var recent = false
    private var expandedStats = true
    private var dialog: Dialog? = null
    private var items = emptyList<Equipment>()
    private var availableRelics = emptyList<Relic>()
    private var pager: InventoryBagPager? = null
    private var pagerKey: List<Any?>? = null
    private var visibleCount = InventoryBagPager.PAGE_SIZE
    private var bagCount: TextView? = null
    private val tabs = mutableMapOf<Page, TextView>()
    private val number = NumberFormat.getNumberInstance(ctx.resources.configuration.locales[0]).apply {
        maximumFractionDigits = 1
    }
    private val header = column()
    private val gold = text("", 14f, accent)
    private val list = RecyclerView(ctx)
    private val selectedEquipment = linkedSetOf<Equipment>()
    private var selectedSaleValue = 0L
    private val selectionBar = column()
    private val selectionCount = text("", 14f, ink, true)
    private val sellSelection = button("") { confirmSellSelection() }
    val isOpen get() = root.visibility == View.VISIBLE

    private class Holder(v: View) : RecyclerView.ViewHolder(v)
    private val adapter = object : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = 1 + when (page) {
            Page.CHARACTER -> 0
            Page.EQUIPMENT -> items.size
            Page.RELICS -> availableRelics.size
        }
        override fun getItemViewType(position: Int) = if (position == 0) 0 else if (page == Page.RELICS) 2 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(if (viewType == 0) header else column().apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            if (position == 0) return
            val box = holder.itemView as LinearLayout
            box.removeAllViews()
            if (page == Page.RELICS) {
                game?.hero?.let { box.addView(relicCard(it, availableRelics[position - 1])) }
                return
            }
            val item = items[position - 1]
            val selected = item in selectedEquipment
            val card = row().apply {
                background = frame(if (selected) accent else item.inventoryColor).apply {
                    if (selected) setColor(0xFF2B3543.toInt())
                }
                isSelected = selected
                setPadding(dp(10), dp(10), dp(10), dp(10))
                minimumHeight = dp(84)
            }
            if (selectedEquipment.isNotEmpty()) card.addView(CheckBox(ctx).apply {
                isChecked = selected
                isClickable = false
                isFocusable = false
                buttonTintList = android.content.res.ColorStateList.valueOf(accent)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(36), dp(48)))
            card.addView(icon(item), LinearLayout.LayoutParams(dp(48), dp(56)))
            val words = column()
            words.addView(text(LootSystem.displayName(ctx, item), 15f, item.inventoryColor, true))
            words.addView(text(subtitle(item), 12f, muted))
            val worn = game?.hero?.equipped?.get(item.slot)
            val rating = LootSystem.rating(item, game?.hero?.archetype)
            val delta = rating - (worn?.let { LootSystem.rating(it, game?.hero?.archetype) } ?: 0)
            words.addView(text(ctx.getString(R.string.inv_rating_line, number.format(rating), signed(delta.toDouble())), 12f,
                if (delta > 0) green else if (delta < 0) red else muted))
            card.addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            card.setOnClickListener {
                if (selectedEquipment.isNotEmpty()) toggleEquipmentSelection(item) else showDetail(item)
            }
            card.setOnLongClickListener {
                if (game?.isExploring == true) { toggleEquipmentSelection(item); true } else false
            }
            box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    init {
        val container = root as LinearLayout
        container.removeAllViews()
        container.setBackgroundColor(0xFF0B101B.toInt())
        container.setPadding(dp(12), dp(8), dp(12), dp(8))
        val toolbar = row()
        toolbar.addView(text(ctx.getString(R.string.roguelike_equipment_title), 20f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(gold)
        toolbar.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = android.content.res.ColorStateList.valueOf(ink)
            background = null
            contentDescription = ctx.getString(R.string.roguelike_inventory_close)
            setOnClickListener { hide() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        container.addView(toolbar)
        val navigation = row()
        Page.entries.forEach { target ->
            val tab = button(ctx.getString(target.label), target == page) { openPage(target) }
            tabs[target] = tab
            navigation.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        container.addView(navigation)
        list.layoutManager = LinearLayoutManager(ctx)
        list.adapter = adapter
        list.itemAnimator = null
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || page != Page.EQUIPMENT) return
                val last = (list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (last >= adapter.itemCount - 6) list.post { loadMoreEquipment() }
            }
        })
        container.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        selectionBar.background = frame(accent)
        selectionBar.setPadding(dp(8), dp(6), dp(8), dp(8))
        val selectionHeader = row()
        selectionHeader.addView(selectionCount, LinearLayout.LayoutParams(0, -2, 1f))
        selectionHeader.addView(button(ctx.getString(R.string.inv_select_all)) { selectAllFiltered() })
        selectionBar.addView(selectionHeader)
        val selectionActions = row()
        selectionActions.addView(button(ctx.getString(android.R.string.cancel)) { cancelSelection() }, LinearLayout.LayoutParams(0, -2, 1f))
        selectionActions.addView(sellSelection, LinearLayout.LayoutParams(0, -2, 1.6f).apply { marginStart = dp(8) })
        selectionBar.addView(selectionActions)
        selectionBar.visibility = View.GONE
        container.addView(selectionBar, LinearLayout.LayoutParams(-1, -2))
        header.layoutParams = RecyclerView.LayoutParams(-1, -2)
    }

    fun show(g: RoguelikeGame) {
        clearSelection()
        game = g
        root.visibility = View.VISIBLE
        visibleCount = InventoryBagPager.PAGE_SIZE
        refresh()
        list.scrollToPosition(0)
    }
    fun hide() { dialog?.dismiss(); dialog = null; clearSelection(); root.visibility = View.GONE }

    /** Android Back leaves selection mode before closing the inventory. */
    fun back() { if (selectedEquipment.isNotEmpty()) cancelSelection() else hide() }

    private fun clearSelection() {
        selectedEquipment.clear()
        selectedSaleValue = 0
        selectionBar.visibility = View.GONE
    }

    private fun cancelSelection() { clearSelection(); updateSelectionUi() }

    private fun toggleEquipmentSelection(item: Equipment) {
        if (selectedEquipment.remove(item)) selectedSaleValue -= LootSystem.sellPrice(item).toLong()
        else { selectedEquipment.add(item); selectedSaleValue += LootSystem.sellPrice(item).toLong() }
        updateSelectionUi()
    }

    private fun selectAllFiltered() {
        if (page != Page.EQUIPMENT || game?.isExploring != true) return
        pager?.matchingItems()?.forEach { item ->
            if (selectedEquipment.add(item)) selectedSaleValue += LootSystem.sellPrice(item).toLong()
        }
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        selectionBar.visibility = if (page == Page.EQUIPMENT && selectedEquipment.isNotEmpty()) View.VISIBLE else View.GONE
        selectionCount.text = ctx.getString(R.string.inv_selection_count, selectedEquipment.size)
        sellSelection.text = ctx.getString(R.string.inv_sell_selection, number.format(selectedSaleValue))
        sellSelection.isEnabled = selectedEquipment.isNotEmpty() && game?.isExploring == true
        if (page == Page.EQUIPMENT && items.isNotEmpty()) adapter.notifyItemRangeChanged(1, items.size)
    }

    private fun confirmSellSelection() {
        val g = game ?: return
        if (!g.isExploring || selectedEquipment.isEmpty()) return
        val confirmedItems = selectedEquipment.toSet()
        val confirmation = android.app.AlertDialog.Builder(ctx, R.style.Theme_Dungeon_Dialog)
            .setTitle(R.string.inv_sell_selection_title)
            .setMessage(ctx.getString(R.string.inv_sell_selection_confirm, confirmedItems.size, number.format(selectedSaleValue)))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.inv_sell_selection_yes) { _, _ ->
                if (g.sellAll(confirmedItems)) {
                    clearSelection()
                    refresh()
                    onChanged()
                } else Toast.makeText(ctx, R.string.inv_sell_selection_failed, Toast.LENGTH_LONG).show()
            }.create()
        dialog = confirmation
        confirmation.setOnDismissListener { if (dialog === confirmation) dialog = null }
        confirmation.show()
        confirmation.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(accent)
        confirmation.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setTextColor(ink)
        applyWindowMode(confirmation)
    }

    fun refresh() {
        val hero = game?.hero ?: return
        gold.text = ctx.getString(R.string.roguelike_hud_gold, DungeonNumbers.format(ctx, hero.gold))
        header.removeAllViews()
        tabs.forEach { (target, tab) ->
            tab.background = frame(if (target == page) accent else 0xFF303C52.toInt())
            tab.setTextColor(if (target == page) ink else muted)
            tab.isSelected = target == page
        }
        when (page) {
            Page.CHARACTER -> { buildCharacter(hero); buildStats(hero) }
            Page.EQUIPMENT -> { prepareEquipment(hero); buildFilters(hero) }
            Page.RELICS -> buildRelics(hero)
        }
        adapter.notifyDataSetChanged()
    }

    private fun openPage(target: Page) {
        if (target != page) clearSelection()
        page = target
        refresh()
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
    }

    private fun prepareEquipment(hero: Hero) {
        // Constant-size cache key: opening an unchanged bag does not scan or rank it again.
        val key = listOf(hero, hero.bag.size, hero.nextLootId, hero.archetype,
            hero.equipped.values.toList(), slot, type, weight, rarity, recent)
        if (key != pagerKey) {
            clearSelection()
            pagerKey = key
            visibleCount = InventoryBagPager.PAGE_SIZE
            val filterSlot = slot
            val filterType = type
            val filterWeight = weight
            val filterRarity = rarity
            pager = InventoryBagPager(hero.bag, recent, hero.archetype) { e ->
            (filterSlot == null || e.slot == filterSlot) && (filterType == null || e.base == filterType) &&
                (filterWeight == null || e.weight == filterWeight) && when (filterRarity) {
                    1 -> e.isotopeZ == null && e.rarity == Rarity.NORMAL
                    2 -> e.isotopeZ == null && e.rarity == Rarity.MAGIC
                    3 -> e.isotopeZ == null && e.rarity == Rarity.RARE
                    4 -> e.isotopeZ != null
                    else -> true
                }
            }
        }
        val current = requireNotNull(pager)
        current.firstPage()
        items = current.through(visibleCount)
    }

    private fun loadMoreEquipment() {
        if (page != Page.EQUIPMENT || !isOpen) return
        val current = pager ?: return
        val last = (list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
        if (last < adapter.itemCount - 6 || items.size >= current.total) return
        val previous = items.size
        visibleCount += InventoryBagPager.PAGE_SIZE
        items = current.through(visibleCount)
        adapter.notifyItemRangeInserted(previous + 1, items.size - previous)
        updateBagCount()
    }

    private fun updateBagCount() {
        bagCount?.text = ctx.getString(R.string.inv_page_count, items.size, pager?.total ?: 0, game?.hero?.bag?.size ?: 0)
    }

    private fun buildCharacter(hero: Hero) {
        section(header, R.string.inv_character)
        header.addView(text(className(hero), 16f, accent, true))
        header.addView(text(ctx.getString(R.string.inv_health, number.format(hero.hp), number.format(hero.maxHp)), 13f, muted))
        val dollRow = row()
        val left = column()
        val right = column()
        listOf(EquipSlot.HELMET, EquipSlot.WEAPON, EquipSlot.AMULET).forEach { left.addView(slotCard(hero, it)) }
        listOf(EquipSlot.CHEST, EquipSlot.OFFHAND, EquipSlot.RING).forEach { right.addView(slotCard(hero, it)) }
        dollRow.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val art = DungeonCombatArt()
        dollRow.addView(object : View(ctx) {
            init { contentDescription = ctx.getString(R.string.inv_character_preview) }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val width = width * .9f
                val left = (getWidth() - width) / 2
                art.drawHero(canvas, RectF(left, height / 2f - width / 2, left + width, height / 2f + width / 2), hero)
            }
        }, LinearLayout.LayoutParams(0, dp(230), 1.15f))
        dollRow.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(dollRow)
        header.addView(slotCard(hero, EquipSlot.BOOTS))
        hero.setArchetype?.let { header.addView(text(ctx.getString(R.string.inv_set_active, ctx.getString(it.labelRes)), 13f, EquipmentArt.LEGENDARY)) }
        header.addView(text(ctx.getString(R.string.inv_slots_hint), 12f, muted))
    }

    private fun slotCard(hero: Hero, slot: EquipSlot): View {
        val item = hero.equipped[slot]
        return column().apply {
            background = frame(item?.inventoryColor ?: 0xFF354058.toInt())
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
            val line = row()
            line.addView(if (item != null) icon(item) else ImageView(ctx).apply {
                setImageDrawable(PixelArtIcon(EquipmentArt.empty(slot)))
                scaleType = ImageView.ScaleType.FIT_XY
                alpha = .4f
            }, LinearLayout.LayoutParams(dp(32), dp(36)))
            line.addView(text(ctx.getString(slot.labelRes), 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            addView(line)
            addView(text(item?.let { LootSystem.displayName(ctx, it) } ?: ctx.getString(R.string.inv_slot_empty), 12f,
                item?.inventoryColor ?: muted, true))
            setOnClickListener {
                if (item != null) showDetail(item) else {
                    this@InventoryPanel.slot = slot
                    type = null
                    weight = null
                    rarity = 0
                    openPage(Page.EQUIPMENT)
                }
            }
        }
    }

    private fun buildStats(hero: Hero) {
        val summary = row()
        for ((label, value) in listOf(
            R.string.roguelike_stattype_weapon_dmg to ctx.getString(R.string.inv_range, number.format(hero.weaponMin), number.format(hero.weaponMax)),
            R.string.roguelike_stattype_armor to number.format(hero.armor),
            R.string.roguelike_stattype_speed to ctx.getString(R.string.inv_percent, number.format(hero.speed * 100)))) {
            summary.addView(column().apply {
                gravity = Gravity.CENTER
                addView(text(ctx.getString(label), 11f, muted))
                addView(text(value, 16f, ink, true))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        header.addView(summary)
        val toggle = button(ctx.getString(if (expandedStats) R.string.inv_stats_less else R.string.inv_stats_more)) {
            expandedStats = !expandedStats; refresh()
        }
        header.addView(toggle)
        if (!expandedStats) return
        header.addView(text(ctx.getString(R.string.inv_floor_stats, hero.floor), 12f, muted))
        val stats = column().apply { background = frame(0xFF29354B.toInt()); setPadding(dp(10), dp(8), dp(10), dp(8)) }
        InventoryStats.values(hero).forEach { stat ->
            val line = row()
            line.addView(text(label(stat), 13f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(text(value(stat), 14f, ink, true))
            line.setPadding(0, dp(5), 0, dp(5))
            stats.addView(line)
        }
        header.addView(stats)
    }

    private fun buildRelics(hero: Hero) {
        section(header, R.string.inv_relic_slots_title)
        header.addView(text(ctx.getString(R.string.inv_relic_slots, hero.relicSlots.count { it != null }, hero.unlockedRelicSlots), 14f, muted))
        for (first in 0 until Hero.RELIC_SLOTS step 2) {
            val slots = row().apply { gravity = Gravity.TOP }
            for (index in first until minOf(first + 2, Hero.RELIC_SLOTS)) {
                val relic = hero.relicSlots[index]
                val unlocked = index < hero.unlockedRelicSlots
                val card = column().apply {
                    background = frame(if (relic != null) accent else 0xFF354058.toInt())
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                }
                card.addView(text(ctx.getString(R.string.inv_relic_slot_number, index + 1), 12f, muted))
                if (relic != null) {
                    card.addView(relicIcon(relic), LinearLayout.LayoutParams(dp(40), dp(40)))
                    card.addView(text(ctx.getString(relic.labelRes), 15f, ink, true))
                    card.addView(text(relicIdentity(relic), 12f, muted))
                    card.addView(text(LexiconText.strip(Lexicon.relicText(LexiconEnv(ctx, hero, hero.floor), relic, hero)), 13f, ink))
                    card.addView(button(ctx.getString(R.string.inv_relic_remove)) { toggleRelic(relic) }.apply {
                        isEnabled = game?.isExploring == true
                    })
                } else {
                    card.addView(text(ctx.getString(if (unlocked) R.string.inv_slot_empty else R.string.inv_relic_locked), 15f, ink, true))
                    if (!unlocked) card.addView(text(ctx.getString(R.string.inv_relic_unlock_floor, Hero.RELIC_SLOT_FLOORS[index - 1]), 13f, muted))
                }
                slots.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            header.addView(slots)
        }
        section(header, R.string.inv_relic_bonuses)
        val known = Resonance.entries.filter { it in hero.knownResonances || it in hero.resonances }
        if (known.isEmpty()) header.addView(text(ctx.getString(R.string.inv_relic_no_bonus), 13f, muted))
        known.forEach { resonance ->
            val active = resonance in hero.resonances
            val bonus = column().apply {
                background = frame(if (active) accent else 0xFF354058.toInt())
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            bonus.addView(text(ctx.getString(R.string.inv_relic_bonus_state, ctx.getString(resonance.labelRes),
                ctx.getString(if (active) R.string.inv_active else R.string.inv_inactive)), 15f, if (active) accent else muted, true))
            bonus.addView(text(ctx.getString(R.string.lex_resonance_pair, ctx.getString(resonance.a.labelRes), ctx.getString(resonance.b.labelRes)), 13f, muted))
            bonus.addView(text(ctx.getString(R.string.lex_resonance_bonus, Resonance.BONUS, ctx.getString(resonance.attribute.labelRes)), 13f, if (active) green else muted))
            bonus.addView(text(LexiconText.strip(ctx.getString(resonance.descRes)), 13f, ink))
            header.addView(bonus, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        header.addView(button(ctx.getString(R.string.lex_button)) { lexicon.open("cat:RELICS") })
        availableRelics = hero.relics.filter { it !in hero.relicSlots }
        section(header, R.string.inv_relic_available)
        header.addView(text(ctx.getString(R.string.inv_relic_available_count, availableRelics.size), 13f, muted))
        if (availableRelics.isEmpty()) header.addView(text(ctx.getString(R.string.inv_relic_available_empty), 14f, muted))
    }

    private fun relicClass(relic: Relic): Archetype? = when {
        relic == Relic.HEAL || relic == Relic.HOURGLASS -> null
        relic.attribute == StatType.CON -> Archetype.WARRIOR
        relic.attribute == StatType.DEX -> Archetype.ROGUE
        relic.attribute == StatType.STR -> Archetype.VAGABOND
        relic.attribute == StatType.WIS -> Archetype.MAGE
        relic.attribute == StatType.INT -> Archetype.NECROMANCER
        else -> null
    }

    private fun relicIdentity(relic: Relic) = ctx.getString(R.string.inv_relic_identity,
        relicClass(relic)?.let { ctx.getString(it.labelRes) } ?: ctx.getString(R.string.inv_relic_neutral),
        ctx.getString(Lexicon.elementRes(relic.element)), ctx.getString(relic.attribute.labelRes))

    private fun relicIcon(relic: Relic) = ImageView(ctx).apply {
        setImageDrawable(SpriteLoader.sheetCell(ctx.assets, relic.iconRow, relic.iconCol)?.let { PixelArtIcon(it) })
        scaleType = ImageView.ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun relicCard(hero: Hero, relic: Relic): View = column().apply {
        background = frame(0xFF354058.toInt())
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        val title = row()
        title.addView(relicIcon(relic), LinearLayout.LayoutParams(dp(44), dp(44)))
        title.addView(text(ctx.getString(relic.labelRes), 17f, ink, true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        addView(title)
        addView(text(relicIdentity(relic), 14f, accent))
        addView(text(LexiconText.strip(Lexicon.relicText(LexiconEnv(ctx, hero, hero.floor), relic, hero)), 14f, ink))
        if (relic.hits || relic.doseCoef > 0f) addView(text(ctx.getString(R.string.inv_relic_power,
            ctx.getString(relic.attribute.labelRes), hero.attribute(relic.attribute), number.format(hero.relicMult(relic))), 13f, muted))
        val pairs = Resonance.entries.filter { (it.a == relic || it.b == relic) && it in hero.knownResonances }
        pairs.forEach { resonance ->
            val partner = if (resonance.a == relic) resonance.b else resonance.a
            addView(text(ctx.getString(R.string.inv_relic_pair_bonus, ctx.getString(partner.labelRes),
                ctx.getString(resonance.labelRes), Resonance.BONUS, ctx.getString(resonance.attribute.labelRes)), 13f,
                if (partner in hero.relicSlots) green else muted))
        }
        addView(button(ctx.getString(R.string.inv_relic_equip), true) { toggleRelic(relic) }.apply {
            isEnabled = game?.isExploring == true
        })
    }

    private fun toggleRelic(relic: Relic) {
        val result = game?.toggleRelic(relic) ?: return
        if (result == Hero.RelicToggle.SLOTS_FULL) Toast.makeText(ctx, R.string.roguelike_inventory_relics_full, Toast.LENGTH_LONG).show()
        refresh()
        onChanged()
    }

    private fun buildFilters(hero: Hero) {
        text(ctx.getString(R.string.inv_bag), 16f, accent, true).apply {
            setPadding(0, dp(18), 0, dp(8))
            header.addView(this)
        }
        bagCount = text("", 13f, muted).also { header.addView(it) }
        updateBagCount()
        filterRow(R.string.inv_filter_slot, listOf(ctx.getString(R.string.inv_all)) + EquipSlot.entries.map { ctx.getString(it.labelRes) },
            slot?.ordinal?.plus(1) ?: 0) { slot = EquipSlot.entries.getOrNull(it - 1); type = null; refresh() }
        val bases = ItemBase.entries.filter { slot == null || it.slot == slot }
        filterRow(R.string.inv_filter_type, listOf(ctx.getString(R.string.inv_all)) + bases.map { ctx.getString(it.nounRes) },
            type?.let { bases.indexOf(it) + 1 } ?: 0) { type = bases.getOrNull(it - 1); refresh() }
        filterRow(R.string.inv_filter_weight, listOf(ctx.getString(R.string.inv_all)) + ArmorWeight.entries.map { ctx.getString(it.labelRes) },
            weight?.ordinal?.plus(1) ?: 0) { weight = ArmorWeight.entries.getOrNull(it - 1); refresh() }
        filterRow(R.string.inv_filter_rarity, listOf(ctx.getString(R.string.inv_all)) + Rarity.entries.map { ctx.getString(it.labelRes) } + ctx.getString(R.string.inv_legendary), rarity) {
            rarity = it; refresh()
        }
        val sorts = row()
        sorts.addView(button(ctx.getString(R.string.roguelike_inventory_sort_best), !recent) { recent = false; refresh() }, LinearLayout.LayoutParams(0, -2, 1f))
        sorts.addView(button(ctx.getString(R.string.roguelike_inventory_sort_recent), recent) { recent = true; refresh() }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(sorts)
        header.addView(text(ctx.getString(R.string.inv_score_hint), 12f, muted))
        if (slot != null || type != null || weight != null || rarity != 0) {
            header.addView(button(ctx.getString(R.string.inv_reset)) { slot = null; type = null; weight = null; rarity = 0; refresh() })
        }
        if (items.isEmpty()) header.addView(text(ctx.getString(if (hero.bag.isEmpty()) R.string.roguelike_inventory_empty else R.string.inv_no_match), 15f, muted).apply {
            setPadding(dp(12), dp(24), dp(12), dp(24))
        })
    }

    private fun filterRow(label: Int, choices: List<String>, selected: Int, change: (Int) -> Unit) {
        val line = row()
        line.addView(text(ctx.getString(label), 13f, muted), LinearLayout.LayoutParams(dp(78), -2))
        line.addView(button(choices.getOrElse(selected) { choices.first() }) {
            android.app.AlertDialog.Builder(ctx, R.style.Theme_Dungeon_Dialog).setTitle(label).setSingleChoiceItems(choices.toTypedArray(), selected) { d, index ->
                d.dismiss(); change(index)
            }.setNegativeButton(android.R.string.cancel, null).show().also { applyWindowMode(it) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(line)
    }

    private fun showDetail(item: Equipment) {
        val hero = game?.hero ?: return
        dialog?.dismiss()
        val worn = hero.equipped[item.slot]
        val equipped = worn == item
        if (!equipped && item !in hero.bag) return
        val modal = Dialog(ctx)
        modal.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog = modal
        val body = column().apply { setPadding(dp(16), dp(8), dp(16), dp(16)); setBackgroundColor(0xFF101827.toInt()) }
        val title = row()
        title.addView(text(ctx.getString(if (equipped) R.string.roguelike_loot_equipped_badge else R.string.inv_comparison), 19f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(button(ctx.getString(R.string.inv_close_detail)) { modal.dismiss() })
        body.addView(title)
        val content = column()
        body.addView(ScrollView(ctx).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val after = if (equipped) hero else InventoryStats.preview(hero, item)
        if (equipped) itemDescription(content, item, after.archetype)
        else {
            val pieces = row().apply { gravity = Gravity.TOP }
            val candidate = column()
            val current = column()
            section(candidate, R.string.inv_candidate)
            itemDescription(candidate, item, after.archetype, compact = true)
            section(current, R.string.inv_current_item)
            if (worn != null) itemDescription(current, worn, hero.archetype, compact = true)
            else current.addView(text(ctx.getString(R.string.roguelike_loot_nothing_equipped), 14f, muted))
            pieces.addView(candidate, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            pieces.addView(current, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
            content.addView(pieces)
        }
        if (!equipped) {
            if (hero.archetype != after.archetype) content.addView(text(ctx.getString(R.string.inv_class_change, className(hero), className(after)), 14f, accent))
            val setBefore = hero.setArchetype?.let { ctx.getString(it.labelRes) } ?: ctx.getString(R.string.inv_none)
            val setAfter = after.setArchetype?.let { ctx.getString(it.labelRes) } ?: ctx.getString(R.string.inv_none)
            if (hero.setArchetype != after.setArchetype) content.addView(text(ctx.getString(R.string.inv_set_change, setBefore, setAfter), 13f, EquipmentArt.LEGENDARY))
            fun element(h: Hero) = h.attackElement?.let { ctx.getString(Lexicon.elementRes(it)) } ?: ctx.getString(R.string.inv_none)
            if (hero.attackElement != after.attackElement) content.addView(text(ctx.getString(R.string.inv_element_change, element(hero), element(after)), 13f, muted))
            fun offhand(h: Hero) = ctx.getString(if (h.archetype?.let { h.classOffhand(it) } == true) R.string.inv_active else R.string.inv_inactive)
            if (offhand(hero) != offhand(after)) content.addView(text(ctx.getString(R.string.inv_offhand_change, offhand(hero), offhand(after)), 13f, muted))
            comparisonHeading(content)
            val beforeStats = InventoryStats.values(hero).associateBy { it.key }
            InventoryStats.values(after).forEach { next ->
                val before = beforeStats[next.key] ?: next.copy(value = 0.0)
                comparisonRow(content, before, next)
            }
        }
        if (!equipped) {
            val actions = row()
            actions.addView(button(ctx.getString(R.string.roguelike_loot_equip_btn), true) {
                game?.equipFromBag(item)
                modal.dismiss(); refresh(); onChanged()
            }.apply { isEnabled = game?.isExploring == true }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button(ctx.getString(R.string.roguelike_inventory_sell, DungeonNumbers.format(ctx, LootSystem.sellPrice(item)))) {
                game?.sell(item)
                modal.dismiss(); refresh(); onChanged()
            }.apply { isEnabled = game?.isExploring == true }, LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(actions)
        }
        scaleDetailText(body)
        modal.setContentView(body)
        modal.setOnDismissListener { if (dialog === modal) dialog = null }
        modal.show()
        modal.window?.setBackgroundDrawableResource(android.R.color.transparent)
        modal.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        applyWindowMode(modal)
    }

    private fun applyWindowMode(modal: Dialog) {
        val window = modal.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (SystemBarsManager.shouldShowSystemBars(ctx)) controller.show(WindowInsetsCompat.Type.systemBars())
        else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun itemDescription(parent: LinearLayout, item: Equipment, archetype: Archetype?, compact: Boolean = false) {
        val line = if (compact) column() else row()
        line.addView(icon(item), LinearLayout.LayoutParams(dp(56), dp(64)))
        val words = column()
        // Keep the name outside lexicon spans: links otherwise override rarity colors.
        words.addView(text(LootSystem.displayName(ctx, item), 16f, item.inventoryColor, true))
        words.addView(text(subtitle(item), 12f, muted))
        line.addView(words, if (compact) LinearLayout.LayoutParams(-1, -2)
            else LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        parent.addView(line)
        parent.addView(text("", 13f, ink).apply {
            setLexiconText(LootSystem.describe(ctx, item, linked = true, archetype = archetype).joinToString("\n")) {
                dialog?.dismiss()
                lexicon.open(it)
            }
        })
    }

    private fun comparisonHeading(parent: LinearLayout) {
        val heading = row().apply { setPadding(dp(4), dp(16), dp(4), dp(4)) }
        for (res in listOf(R.string.inv_selected_column, R.string.inv_equipped_column, R.string.inv_delta)) {
            heading.addView(text(ctx.getString(res), 11f, muted).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        parent.addView(heading)
    }

    private fun comparisonRow(parent: LinearLayout, before: InventoryStats.Value, after: InventoryStats.Value) {
        val diff = after.value - before.value
        val changed = abs(diff) >= .05
        val color = if (!changed) muted else if ((diff > 0) != after.lowerIsBetter) green else red
        val line = column().apply {
            setPadding(dp(4), dp(8), dp(4), dp(8))
            if (changed) setBackgroundColor(0xFF1B293C.toInt())
        }
        line.addView(text(label(after), 12f, ink))
        val values = row()
        for ((str, tint) in listOf(value(after) to ink, value(before) to muted,
            (if (changed) signed(diff) else ctx.getString(R.string.roguelike_delta_equal)) to color)) {
            values.addView(text(str, 12f, tint).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        line.addView(values)
        parent.addView(line)
    }

    /** Enlarge every label and action in the detail sheet, preserving system font scaling. */
    private fun scaleDetailText(view: View) {
        if (view is TextView) view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, view.textSize * 1.4f)
        if (view is ViewGroup) for (index in 0 until view.childCount) scaleDetailText(view.getChildAt(index))
    }

    private fun label(stat: InventoryStats.Value) = stat.name?.let { ctx.getString(stat.label, ctx.getString(it)) } ?: ctx.getString(stat.label)
    private fun value(stat: InventoryStats.Value): String {
        val n = number.format(stat.value)
        return when (stat.unit) {
            InventoryStats.Unit.NUMBER -> n
            InventoryStats.Unit.PERCENT -> ctx.getString(R.string.inv_percent, n)
            InventoryStats.Unit.MS -> ctx.getString(R.string.inv_ms, n)
            InventoryStats.Unit.TURNS -> ctx.getString(R.string.inv_turns, n)
        }
    }
    private fun signed(n: Double) = ctx.getString(if (n >= 0) R.string.inv_positive else R.string.inv_negative, number.format(abs(n)))
    private fun className(hero: Hero) = hero.archetype?.let { ctx.getString(it.labelRes) } ?: ctx.getString(R.string.inv_no_class)
    private fun subtitle(item: Equipment) = listOfNotNull(ctx.getString(item.slot.labelRes),
        item.weight?.let { ctx.getString(it.labelRes) }, ctx.getString(if (item.isotopeZ != null) R.string.inv_legendary else item.rarity.labelRes)).joinToString(ctx.getString(R.string.inv_separator))
    private fun icon(item: Equipment) = ImageView(ctx).apply {
        setImageDrawable(PixelArtIcon(EquipmentArt.icon(item)))
        scaleType = ImageView.ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun dp(n: Int) = (n * density).toInt()
    private fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun section(parent: LinearLayout, res: Int) { parent.addView(text(ctx.getString(res), 16f, accent, true).apply { setPadding(0, dp(18), 0, dp(8)) }) }
    private fun button(label: String, active: Boolean = false, action: () -> Unit) = text(label, 13f, if (active) ink else muted, active).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = frame(if (active) accent else 0xFF303C52.toInt())
        setOnClickListener { action() }
    }
    private fun horizontal(parent: LinearLayout, child: View) { parent.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(child) }) }
    private fun frame(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat(); setColor(0xFF151F30.toInt()); setStroke(dp(1).coerceAtLeast(1), color)
    }
}
