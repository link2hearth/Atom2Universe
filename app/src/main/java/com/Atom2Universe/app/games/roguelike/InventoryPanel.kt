package com.Atom2Universe.app.games.roguelike

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.SystemBarsManager
import java.text.NumberFormat

/** Separate character, paginated equipment and relic pages. */
class InventoryPanel(
    private val root: View,
    private val lexicon: LexiconPanel,
    private val onChanged: () -> Unit,
    private val onClosed: () -> Unit = {},
) {
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
    private var filterClass: Archetype? = null
    private var rarity = 0
    private var recent = false
    private var expandedStats = true
    private var dialog: Dialog? = null
    private var equipmentPopup: PopupWindow? = null
    private var characterArea: FrameLayout? = null
    private var inventoryDetail: Equipment? = null
    private var expandedItem: Equipment? = null
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
            val expanded = expandedItem == item && selectedEquipment.isEmpty()
            val tile = column().apply {
                background = frame(if (selected || expanded) accent else item.inventoryColor)
                isSelected = selected || expanded
            }
            val card = row().apply {
                if (selected) setBackgroundColor(0xFF2B3543.toInt())
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
            words.addView(text(ctx.getString(R.string.inv_rating, number.format(rating)), 12f,
                if (delta > 0) green else if (delta < 0) red else muted))
            card.addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            card.setOnClickListener {
                if (selectedEquipment.isNotEmpty()) toggleEquipmentSelection(item) else toggleDetail(item, box.top)
            }
            card.setOnLongClickListener {
                if (game?.isExploring == true) { toggleEquipmentSelection(item); true } else false
            }
            tile.addView(card)
            if (expanded) tile.addView(comparisonDetail(item))
            box.addView(tile, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    init {
        val container = root as LinearLayout
        container.removeAllViews()
        container.setBackgroundColor(0xFF0B101B.toInt())
        container.setPadding(dp(12), dp(10), dp(12), dp(8))
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
    fun hide() {
        val wasOpen = isOpen
        equipmentPopup?.dismiss()
        dialog?.dismiss(); dialog = null; clearSelection(); root.visibility = View.GONE
        if (wasOpen) onClosed()
    }

    /** Android Back leaves selection mode before closing the inventory. */
    fun back() {
        if (equipmentPopup != null) equipmentPopup?.dismiss()
        else if (page == Page.EQUIPMENT && expandedItem != null) {
            val index = items.indexOf(expandedItem)
            expandedItem = null
            if (index >= 0) adapter.notifyItemChanged(index + 1)
        }
        else if (page == Page.EQUIPMENT && inventoryDetail != null) closeInventoryDetail()
        else if (selectedEquipment.isNotEmpty()) cancelSelection()
        else hide()
    }

    private fun clearSelection() {
        selectedEquipment.clear()
        selectedSaleValue = 0
        selectionBar.visibility = View.GONE
    }

    private fun cancelSelection() { clearSelection(); updateSelectionUi() }

    private fun toggleEquipmentSelection(item: Equipment) {
        val expandedIndex = items.indexOf(expandedItem)
        expandedItem = null
        if (expandedIndex >= 0) adapter.notifyItemChanged(expandedIndex + 1)
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
        equipmentPopup?.dismiss()
        characterArea = null
        header.removeAllViews()
        tabs.forEach { (target, tab) ->
            tab.background = frame(if (target == page) accent else 0xFF303C52.toInt())
            tab.setTextColor(if (target == page) ink else muted)
            tab.isSelected = target == page
        }
        when (page) {
            Page.CHARACTER -> { buildCharacter(hero); buildStats(hero) }
            Page.EQUIPMENT -> {
                prepareEquipment(hero); buildCharacter(hero); buildFilters(hero)
                inventoryDetail?.let { item ->
                    if (hero.equipped[item.slot] != item) inventoryDetail = null
                    else showEquippedDetail(item)
                }
            }
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
            hero.equipped.values.toList(), slot, filterClass, rarity, recent)
        if (key != pagerKey) {
            expandedItem = null
            clearSelection()
            pagerKey = key
            visibleCount = InventoryBagPager.PAGE_SIZE
            val filterSlot = slot
            val selectedClass = filterClass
            val filterRarity = rarity
            pager = InventoryBagPager(hero.bag, recent, hero.archetype) { e ->
            (filterSlot == null || e.slot == filterSlot) &&
                (selectedClass == null || when (e.slot) {
                    EquipSlot.HELMET, EquipSlot.CHEST, EquipSlot.BOOTS -> e.weight == selectedClass.weight
                    EquipSlot.WEAPON -> selectedClass.accepts(e.base)
                    EquipSlot.OFFHAND -> e.base == selectedClass.offhand
                    EquipSlot.AMULET, EquipSlot.RING -> true
                }) && when (filterRarity) {
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
        val area = column()
        val frame = FrameLayout(ctx)
        characterArea = frame
        frame.addView(area, FrameLayout.LayoutParams(-1, -2))
        header.addView(frame, LinearLayout.LayoutParams(-1, -2))
        val dollRow = row()
        val left = column()
        val right = column()
        listOf(EquipSlot.HELMET, EquipSlot.CHEST).forEach { left.addView(slotCard(hero, it)) }
        listOf(EquipSlot.AMULET, EquipSlot.RING).forEach { right.addView(slotCard(hero, it)) }
        dollRow.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val art = DungeonCombatArt()
        dollRow.addView(object : View(ctx) {
            init { contentDescription = ctx.getString(R.string.inv_character_preview) }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val width = minOf(width, height) * .8f
                val left = (getWidth() - width) / 2
                art.drawHero(canvas, RectF(left, height / 2f - width / 2, left + width, height / 2f + width / 2), hero)
            }
        }, LinearLayout.LayoutParams(0, dp(160), 1f))
        dollRow.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        area.addView(dollRow)
        val bottomRow = row().apply { gravity = Gravity.TOP }
        listOf(EquipSlot.BOOTS, EquipSlot.WEAPON, EquipSlot.OFFHAND).forEachIndexed { index, slot ->
            bottomRow.addView(slotCard(hero, slot),
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    topMargin = dp(6)
                    if (index == 1) {
                        marginStart = dp(4)
                        marginEnd = dp(4)
                    }
                })
        }
        area.addView(bottomRow)
        hero.setArchetype?.let { header.addView(text(ctx.getString(R.string.inv_set_active, ctx.getString(it.labelRes)), 13f, EquipmentArt.LEGENDARY)) }
    }

    private fun slotCard(hero: Hero, slot: EquipSlot): View {
        val item = hero.equipped[slot]
        return column().apply {
            background = frame(item?.inventoryColor ?: 0xFF354058.toInt())
            if (page == Page.EQUIPMENT && this@InventoryPanel.slot == slot) {
                (background as GradientDrawable).setStroke(dp(3), accent)
                isSelected = true
            }
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
            if (item != null) addView(scoreLabel(item, hero.archetype))
            setOnClickListener {
                if (page == Page.EQUIPMENT) {
                    this@InventoryPanel.slot = if (this@InventoryPanel.slot == slot) null else slot
                    inventoryDetail = item.takeIf { this@InventoryPanel.slot != null }
                    refresh()
                } else if (item != null) showEquippedDetail(item) else {
                    this@InventoryPanel.slot = slot
                    rarity = 0
                    openPage(Page.EQUIPMENT)
                }
            }
        }
    }

    private fun buildStats(hero: Hero) {
        val summary = row()
        for ((label, value) in listOf(
            R.string.roguelike_stattype_armor to number.format(hero.armor),
            R.string.roguelike_stattype_weapon_dmg to ctx.getString(R.string.inv_range, number.format(hero.weaponMin), number.format(hero.weaponMax)),
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
        relic.attribute == StatType.STR -> Archetype.BARBARIAN
        relic.attribute == StatType.CON -> Archetype.WARRIOR
        relic.attribute == StatType.DEX -> Archetype.ROGUE
        relic.attribute == StatType.END -> Archetype.VAGABOND
        relic.attribute == StatType.INT -> Archetype.MAGE
        relic.attribute == StatType.WIS -> Archetype.NECROMANCER
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
        val filters = row()
        filterButton(filters, R.string.inv_filter_class, listOf(ctx.getString(R.string.inv_all)) + Archetype.entries.map { ctx.getString(it.labelRes) },
            filterClass?.ordinal?.plus(1) ?: 0) { filterClass = Archetype.entries.getOrNull(it - 1); refresh() }
        filterButton(filters, R.string.inv_filter_rarity, listOf(ctx.getString(R.string.inv_all)) + Rarity.entries.map { ctx.getString(it.labelRes) } + ctx.getString(R.string.inv_legendary), rarity) {
            rarity = it; refresh()
        }
        filters.addView(button(ctx.getString(if (recent) R.string.roguelike_inventory_sort_recent else R.string.roguelike_inventory_sort_best)) {
            recent = !recent; refresh()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(filters)
        if (slot != null || filterClass != null || rarity != 0) {
            header.addView(button(ctx.getString(R.string.inv_reset)) { slot = null; inventoryDetail = null; filterClass = null; rarity = 0; refresh() })
        }
        if (items.isEmpty()) header.addView(text(ctx.getString(if (hero.bag.isEmpty()) R.string.roguelike_inventory_empty else R.string.inv_no_match), 15f, muted).apply {
            setPadding(dp(12), dp(24), dp(12), dp(24))
        })
    }

    private fun filterButton(parent: LinearLayout, label: Int, choices: List<String>, selected: Int, change: (Int) -> Unit) {
        parent.addView(button(ctx.getString(R.string.inv_filter_value, ctx.getString(label), choices.getOrElse(selected) { choices.first() }), selected != 0) {
            android.app.AlertDialog.Builder(ctx, R.style.Theme_Dungeon_Dialog).setTitle(label).setSingleChoiceItems(choices.toTypedArray(), selected) { d, index ->
                d.dismiss(); change(index)
            }.setNegativeButton(android.R.string.cancel, null).show().also { applyWindowMode(it) }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
    }

    private fun closeInventoryDetail() {
        inventoryDetail = null
        slot = null
        refresh()
    }

    private fun showEquippedDetail(item: Equipment) {
        val g = game ?: return
        val area = characterArea ?: return
        val inline = page == Page.EQUIPMENT
        if (g.hero.equipped[item.slot] != item) return
        if (!inline && (area.width == 0 || area.height == 0)) return
        equipmentPopup?.dismiss()
        val popup = PopupWindow(ctx)
        fun close() {
            if (inline) closeInventoryDetail() else popup.dismiss()
        }
        val body = column().apply {
            background = frame(item.inventoryColor)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { close() }
        }
        val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                close()
                return true
            }
        })
        val content = column()
        val title = row()
        title.addView(icon(item), LinearLayout.LayoutParams(dp(36), dp(40)))
        title.addView(text(LootSystem.displayName(ctx, item), 15f, item.inventoryColor, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        if (inline) title.addView(button(ctx.getString(R.string.inv_unequip), true) {
            if (g.unequip(item)) {
                inventoryDetail = null
                slot = null
                refresh()
                onChanged()
            }
        }.apply { isEnabled = g.isExploring }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        title.setOnClickListener { close() }
        body.addView(title)
        content.addView(text(subtitle(item), 12f, muted))
        content.addView(scoreLabel(item, g.hero.archetype))
        content.addView(text(LootSystem.describe(ctx, item, linked = false, archetype = g.hero.archetype)
            .joinToString("\n"), 13f, ink))
        body.addView(object : ScrollView(ctx) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                val handled = super.dispatchTouchEvent(event)
                taps.onTouchEvent(event)
                return handled
            }
        }.apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        if (inline) {
            // Keep the character measured underneath the sheet. Build both before layout:
            // replacing children from a layout callback can leave them unmeasured in RecyclerView.
            area.getChildAt(0).visibility = View.INVISIBLE
            area.addView(body, FrameLayout.LayoutParams(-1, -1))
            return
        }
        popup.contentView = body
        popup.width = area.width
        popup.height = area.height
        popup.isFocusable = true
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        popup.setOnDismissListener { if (equipmentPopup === popup) equipmentPopup = null }
        equipmentPopup = popup
        popup.showAsDropDown(area, 0, -area.height, Gravity.START)
    }

    private fun toggleDetail(item: Equipment, top: Int) {
        val previous = items.indexOf(expandedItem)
        expandedItem = if (expandedItem == item) null else item
        val current = items.indexOf(item)
        if (previous >= 0 && previous != current) adapter.notifyItemChanged(previous + 1)
        if (current >= 0) {
            adapter.notifyItemChanged(current + 1)
            // Keep the touched tile in place when another expanded tile above it collapses.
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(current + 1, top)
        }
    }

    /** Part of the selected tile: normal list content, with no overlay or nested scrolling. */
    private fun comparisonDetail(item: Equipment): View {
        val g = requireNotNull(game)
        val hero = g.hero
        val body = column().apply { setPadding(dp(8), 0, dp(8), dp(8)) }
        val content = row().apply { gravity = Gravity.TOP }
        val candidate = column().apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        val current = column().apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        val worn = hero.equipped[item.slot]
        val candidateScore = LootSystem.rating(item, hero.archetype)
        val wornScore = worn?.let { LootSystem.rating(it, hero.archetype) } ?: 0
        fun scoreColor(score: Int, other: Int) = if (score > other) green else if (score < other) red else muted
        candidate.addView(text(ctx.getString(R.string.inv_candidate), 15f, accent, true))
        val after = InventoryStats.preview(hero, item)
        itemDescription(candidate, item, after.archetype)
        candidate.addView(scoreLabel(item, hero.archetype, scoreColor(candidateScore, wornScore)))
        if (hero.archetype != after.archetype) {
            candidate.addView(text(ctx.getString(R.string.inv_class_change, className(hero), className(after)), 13f, accent))
        }
        current.addView(text(ctx.getString(R.string.inv_current_item), 15f, accent, true))
        if (worn != null) {
            itemDescription(current, worn, hero.archetype)
            current.addView(scoreLabel(worn, hero.archetype, scoreColor(wornScore, candidateScore)))
        } else current.addView(text(ctx.getString(R.string.roguelike_loot_nothing_equipped), 13f, muted))
        content.addView(candidate, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(View(ctx).apply { setBackgroundColor(0xFF354058.toInt()) },
            LinearLayout.LayoutParams(dp(1), -1))
        content.addView(current, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(content)
        val actions = row()
        actions.addView(button(ctx.getString(R.string.roguelike_loot_equip_btn), true) {
            g.equipFromBag(item)
            expandedItem = null; refresh(); onChanged()
        }.apply { isEnabled = g.isExploring }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button(ctx.getString(R.string.roguelike_inventory_sell, DungeonNumbers.format(ctx, LootSystem.sellPrice(item)))) {
            g.sell(item)
            expandedItem = null; refresh(); onChanged()
        }.apply { isEnabled = g.isExploring }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(actions)
        return body
    }

    private fun scoreLabel(item: Equipment, archetype: Archetype?, color: Int = muted) =
        text(ctx.getString(R.string.inv_rating, number.format(LootSystem.rating(item, archetype))), 13f, color, true)
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

    private fun itemDescription(parent: LinearLayout, item: Equipment, archetype: Archetype?) {
        parent.addView(text(LootSystem.describe(ctx, item, linked = false, archetype = archetype)
            .joinToString("\n"), 15f, ink))
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
