package com.Atom2Universe.app.games.caves

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.CraftDef
import com.Atom2Universe.app.games.caves.node.CraftRegistry
import com.Atom2Universe.app.games.caves.node.ItemRarity
import com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry

internal enum class InvGpZone { GRID, HOTBAR, CRAFTING }

internal class InventoryManager(private val activity: CaveActivity) {

    private val renderer get() = activity.renderer
    private val hud      get() = activity.hud

    // ── Slots ─────────────────────────────────────────────────────────────────
    val invSlots = ArrayList<Short?>()
    var invSlotsReady = false
    var selectedSlotIdx = -1
    var dragSourceIdx = -1

    fun hotbarBase() = (invSlots.size - CaveActivity.ACTIVE_SIZE).coerceAtLeast(0)
    fun selectedType(): Short? = selectedSlotIdx.takeIf { it in invSlots.indices }?.let { invSlots[it] }

    // ── Crafting ──────────────────────────────────────────────────────────────
    var selectedRecipe: CraftDef? = null
    var craftingAdapter: CraftingAdapter? = null

    // ── Refs views de l'overlay ───────────────────────────────────────────────
    var infoSpriteView: View? = null
    var infoNameTv: TextView? = null
    var infoCountTv: TextView? = null
    var infoDivider: View? = null
    var infoIngredientsTv: TextView? = null
    var craftingEmptyTv: View? = null
    var craftingRecyclerView: RecyclerView? = null
    var rightHeaderTv: TextView? = null
    var sellPanel: View? = null
    var sellPriceTv: TextView? = null
    var sellButton: Button? = null

    // ── Pager inventaire ──────────────────────────────────────────────────────
    var invPager: ViewPager2? = null
    var pageIndicatorTv: TextView? = null
    var pagedAdapter: PagedInvPagerAdapter? = null
    var pageSize = CaveActivity.GRID_COLS * 5
    var currentPage = 0

    // ── Gamepad inventaire ────────────────────────────────────────────────────
    var invGpZone = InvGpZone.HOTBAR
    var invGpCursor = 0
    private var invGpLastMoveMs = 0L
    private var invGpRightLastMs = 0L
    private val INV_GP_REPEAT_MS = 170L

    // ── Init overlay ──────────────────────────────────────────────────────────

    fun setupOverlay(invOverlay: View) {
        invOverlay.findViewById<View>(R.id.cave_inv_dim_area).setOnClickListener { closeInventory() }
        invOverlay.findViewById<View>(R.id.cave_inv_panel).setOnClickListener { /* consomme */ }
        invOverlay.findViewById<View>(R.id.cave_inv_info_column).setOnClickListener { closeInventory() }

        infoSpriteView       = invOverlay.findViewById(R.id.cave_inv_info_sprite)
        infoNameTv           = invOverlay.findViewById(R.id.cave_inv_info_name)
        infoCountTv          = invOverlay.findViewById(R.id.cave_inv_info_count)
        infoDivider          = invOverlay.findViewById(R.id.cave_inv_info_divider)
        infoIngredientsTv    = invOverlay.findViewById(R.id.cave_inv_info_ingredients)
        craftingEmptyTv      = invOverlay.findViewById(R.id.cave_inv_crafting_empty)
        craftingRecyclerView = invOverlay.findViewById(R.id.cave_inv_crafting_recycler)
        rightHeaderTv        = invOverlay.findViewById(R.id.cave_inv_right_header)
        sellPanel            = invOverlay.findViewById(R.id.cave_inv_sell_panel)
        sellPriceTv          = invOverlay.findViewById(R.id.cave_inv_sell_price)
        sellButton           = invOverlay.findViewById<Button>(R.id.cave_inv_sell_btn)
            ?.also { btn -> btn.setOnClickListener { selectedType()?.let { doSell(it) } } }
        invPager             = invOverlay.findViewById(R.id.cave_inv_pager)
        pageIndicatorTv      = invOverlay.findViewById(R.id.cave_inv_page_indicator)

        val ca = CraftingAdapter(emptyList()).also { craftingAdapter = it }
        craftingRecyclerView?.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = ca
        }

        invPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                if (invGpZone == InvGpZone.GRID) {
                    val pageStart = currentPage * pageSize
                    val pageEnd   = minOf(pageStart + pageSize, hotbarBase()).coerceAtLeast(pageStart + 1)
                    val localCol  = invGpCursor % CaveActivity.GRID_COLS
                    invGpCursor   = (pageStart + localCol).coerceAtMost(pageEnd - 1)
                }
                updatePageIndicator()
                pagedAdapter?.notifyDataSetChanged()
                hud.updateHotbarForInventory()
            }
        })

        // Drag-and-drop sur la hotbar réelle pendant l'inventaire
        for (i in 0 until CaveActivity.ACTIVE_SIZE) {
            val sv = hud.slotViews[i] ?: continue
            sv.setOnDragListener(makeSlotDragListener { hotbarBase() + i })
            sv.setOnLongClickListener {
                if (activity.invOverlay.visibility == View.VISIBLE) {
                    startSlotDrag(it, hotbarBase() + i); true
                } else false
            }
        }
    }

    // ── Gestion slots ─────────────────────────────────────────────────────────

    fun initInvSlots() {
        invSlots.clear()
        val hotbarTypes = (0 until CaveActivity.ACTIVE_SIZE).mapNotNull { i ->
            renderer.hotbar[i]?.takeIf { (renderer.inventory[it] ?: 0) > 0 }
        }.toSet()
        val gridTypes = renderer.inventory
            .filter { (t, c) -> c > 0 && t !in hotbarTypes }
            .keys.sortedBy { it }
        for (t in gridTypes) invSlots.add(t)
        repeat(CaveActivity.EMPTY_BUFFER) { invSlots.add(null) }
        for (i in 0 until CaveActivity.ACTIVE_SIZE) {
            val t = renderer.hotbar[i]
            invSlots.add(if (t != null && (renderer.inventory[t] ?: 0) > 0) t else null)
        }
        invSlotsReady = true
        syncHotbar()
    }

    fun addNewType(type: Short) {
        val base = hotbarBase()
        val hotbarSlot = (base until base + CaveActivity.ACTIVE_SIZE).firstOrNull { invSlots.getOrNull(it) == null }
        if (hotbarSlot != null) { invSlots[hotbarSlot] = type; return }
        val gridSlot = (0 until base).firstOrNull { invSlots[it] == null }
        if (gridSlot != null) { invSlots[gridSlot] = type; return }
        invSlots.add(base, type)
    }

    fun syncHotbar() {
        val base = hotbarBase()
        for (i in 0 until CaveActivity.ACTIVE_SIZE) renderer.hotbar[i] = invSlots.getOrNull(base + i)
        renderer.hotbarCallback?.invoke(renderer.hotbar.copyOf(), renderer.selectedSlot)
    }

    fun swapSlots(a: Int, b: Int) {
        if (a == b || a !in invSlots.indices || b !in invSlots.indices) return
        val tmp = invSlots[a]; invSlots[a] = invSlots[b]; invSlots[b] = tmp
        syncHotbar(); activity.saveWorldAsync()
    }

    // ── Drag & Drop ───────────────────────────────────────────────────────────

    fun startSlotDrag(view: View, idx: Int) {
        if (invSlots.getOrNull(idx) == null) return
        dragSourceIdx = idx
        val clip = ClipData.newPlainText("slot", idx.toString())
        view.startDragAndDrop(clip, View.DragShadowBuilder(view), idx, 0)
    }

    fun makeSlotDragListener(idxProvider: () -> Int): View.OnDragListener =
        View.OnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.55f; true }
                DragEvent.ACTION_DRAG_EXITED  -> { v.alpha = 1f; true }
                DragEvent.ACTION_DROP         -> {
                    v.alpha = 1f
                    val target = idxProvider()
                    if (dragSourceIdx >= 0 && target != dragSourceIdx) {
                        swapSlots(dragSourceIdx, target)
                        dragSourceIdx = -1
                        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; dragSourceIdx = -1; true }
                else -> false
            }
        }

    // ── Inventaire changed ────────────────────────────────────────────────────

    fun onInventoryChanged(inv: Map<Short, Int>) {
        for (i in 0 until CaveActivity.ACTIVE_SIZE) {
            val t = renderer.hotbar[i] ?: continue
            if ((inv[t] ?: 0) <= 0) renderer.hotbar[i] = null
        }
        if (invSlotsReady) {
            for (i in invSlots.indices) {
                val t = invSlots[i] ?: continue
                if ((inv[t] ?: 0) <= 0) invSlots[i] = null
            }
            val existing = invSlots.filterNotNull().toSet()
            val base = hotbarBase()
            for ((type, count) in inv) {
                if (count > 0 && type !in existing) {
                    val hotbarIdx = renderer.hotbar.indexOfFirst { it == type }
                    val directSlot = if (hotbarIdx >= 0) base + hotbarIdx else -1
                    if (directSlot in invSlots.indices) invSlots[directSlot] = type else addNewType(type)
                }
            }
            if (selectedSlotIdx >= invSlots.size) selectedSlotIdx = -1
            syncHotbar()
            if (activity.invOverlay.visibility == View.VISIBLE) {
                refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
            }
        } else {
            renderer.hotbarCallback?.invoke(renderer.hotbar.copyOf(), renderer.selectedSlot)
        }
    }

    // ── Inventaire open/close ─────────────────────────────────────────────────

    fun openInventory() {
        if (!invSlotsReady) initInvSlots()
        currentPage = 0
        activity.invOverlay.visibility = View.VISIBLE
        invGpZone   = InvGpZone.HOTBAR
        invGpCursor = hotbarBase()
        activity.invOverlay.post {
            if (activity.invOverlay.visibility != View.VISIBLE) return@post
            // Calcule la taille de page selon l'espace réel du pager
            invPager?.let { pager ->
                if (pager.width > 0) {
                    val cellSize = pager.width / CaveActivity.GRID_COLS
                    val rows = if (cellSize > 0) (pager.height / cellSize).coerceAtLeast(1) else 5
                    pageSize = CaveActivity.GRID_COLS * rows
                }
                val cellSize = if (pager.width > 0) pager.width / CaveActivity.GRID_COLS
                               else (52 * activity.resources.displayMetrics.density).toInt()
                if (pagedAdapter == null) {
                    pagedAdapter = PagedInvPagerAdapter(cellSize).also { pager.adapter = it }
                } else {
                    pagedAdapter!!.cellSize = cellSize
                    pager.adapter = pagedAdapter
                }
            }
            refreshPagedAdapter()
            hud.updateHotbarForInventory()
            updateInfoPanel()
            updateCraftingList()
        }
    }

    fun closeInventory() {
        selectedSlotIdx = -1
        selectedRecipe  = null
        invGpZone       = InvGpZone.HOTBAR
        invGpCursor     = 0
        activity.invOverlay.visibility = View.GONE
        hud.updateHotbarUI(renderer.hotbar, renderer.selectedSlot)
    }

    fun refreshPagedAdapter() {
        val adapter = pagedAdapter ?: return
        val pc = pageCount()
        if (currentPage >= pc) currentPage = (pc - 1).coerceAtLeast(0)
        adapter.notifyDataSetChanged()
        invPager?.setCurrentItem(currentPage, false)
        updatePageIndicator()
    }

    private fun pageCount() = ((hotbarBase() + pageSize - 1) / pageSize).coerceAtLeast(1)

    private fun updatePageIndicator() {
        pageIndicatorTv?.text = "${currentPage + 1} / ${pageCount()}"
    }

    // ── Panneau info ──────────────────────────────────────────────────────────

    fun updateInfoPanel() {
        val recipe = selectedRecipe
        val dp = activity.resources.displayMetrics.density
        if (recipe != null) {
            infoSpriteView?.background = activity.blockDrawable(recipe.result, 6f)
            infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
            infoNameTv?.text  = activity.blockName(recipe.result)
            infoCountTv?.text = "×${recipe.resultCount}"
            infoIngredientsTv?.text = recipe.ingredients.joinToString("\n") { (t, n) -> "${activity.blockName(t)} ×$n" }
            infoDivider?.visibility       = View.VISIBLE
            infoIngredientsTv?.visibility = View.VISIBLE
        } else {
            val type = selectedType()
            val isWeapon = type != null && WeaponInstanceRegistry.isWeapon(type)
            if (isWeapon) {
                val instance = WeaponInstanceRegistry.get(type!!)
                val def = instance?.let { com.Atom2Universe.app.games.caves.node.ItemRegistry.get(it.defId) }
                infoSpriteView?.background = activity.blockDrawable(type, 6f)
                val rarityColor = weaponRarityColor(instance?.rarity ?: ItemRarity.COMMON)
                val rarityLabel = instance?.rarity?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "?"
                val baseName    = def?.id?.replace('_', ' ')?.split(" ")?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } } ?: "Unknown"
                infoNameTv?.setTextColor(rarityColor)
                infoNameTv?.text  = "[$rarityLabel]\n$baseName"
                infoCountTv?.text = ""
                infoDivider?.visibility = View.VISIBLE
                val dmg   = instance?.rolledDamage ?: 0
                val speed = def?.attackSpeedMs?.let { "${it}ms" } ?: ""
                val extra = instance?.rolledStats?.entries?.joinToString("\n") { (k, v) ->
                    val label = affixLabel(k)
                    val suffix = affixSuffix(k)
                    "$label: $v$suffix"
                } ?: ""
                infoIngredientsTv?.text = buildString {
                    append("⚔ $dmg dmg")
                    if (speed.isNotEmpty()) append("  •  $speed")
                    if (extra.isNotEmpty()) { append("\n"); append(extra) }
                }
                infoIngredientsTv?.visibility = View.VISIBLE
            } else {
                infoDivider?.visibility       = View.GONE
                infoIngredientsTv?.visibility = View.GONE
                if (type == null) {
                    infoSpriteView?.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; setColor(0x33FFFFFF)
                        cornerRadius = 6 * dp
                    }
                    infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                    infoNameTv?.text  = activity.getString(R.string.cave_inv_info_empty)
                    infoCountTv?.text = ""
                } else {
                    infoSpriteView?.background = activity.blockDrawable(type, 6f)
                    infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                    infoNameTv?.text  = activity.blockName(type)
                    infoCountTv?.text = "×${renderer.inventory[type] ?: 0}"
                }
            }
        }
    }

    private fun affixLabel(key: String): String = when (key) {
        "crit_chance"   -> activity.getString(R.string.cave_affix_crit_chance)
        "crit_dmg"      -> activity.getString(R.string.cave_affix_crit_dmg)
        "attack_speed"  -> activity.getString(R.string.cave_affix_attack_speed)
        "life_steal"    -> activity.getString(R.string.cave_affix_life_steal)
        "bleed_chance"  -> activity.getString(R.string.cave_affix_bleed_chance)
        "shock_chance"  -> activity.getString(R.string.cave_affix_shock_chance)
        "execute"       -> activity.getString(R.string.cave_affix_execute)
        "aoe_splash"    -> activity.getString(R.string.cave_affix_aoe_splash)
        "thorns"        -> activity.getString(R.string.cave_affix_thorns)
        "poison_chance" -> activity.getString(R.string.cave_affix_poison_chance)
        "fire_chance"   -> activity.getString(R.string.cave_affix_fire_chance)
        else            -> key.replace('_', ' ').split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun affixSuffix(key: String): String = when (key) {
        "crit_dmg" -> "%"
        "thorns"   -> "%"
        else       -> "%"
    }

    private fun weaponRarityColor(rarity: ItemRarity) = when (rarity) {
        ItemRarity.COMMON    -> 0xFFAAAAAA.toInt()
        ItemRarity.MAGIC     -> 0xFF4488FF.toInt()
        ItemRarity.RARE      -> 0xFFFFDD00.toInt()
        ItemRarity.EPIC      -> 0xFFCC44FF.toInt()
        ItemRarity.LEGENDARY -> 0xFFFF8800.toInt()
    }

    private fun weaponSellPrice(id: Short): Int {
        val inst = WeaponInstanceRegistry.get(id) ?: return 0
        val tierMult = inst.tier.coerceAtLeast(1) * 5
        val rarityMult = when (inst.rarity) {
            ItemRarity.COMMON    -> 1
            ItemRarity.MAGIC     -> 2
            ItemRarity.RARE      -> 4
            ItemRarity.EPIC      -> 8
            ItemRarity.LEGENDARY -> 15
        }
        return tierMult * rarityMult
    }

    // ── Crafting ──────────────────────────────────────────────────────────────

    fun updateCraftingList() {
        val type = selectedType()
        val isWeapon = type != null && WeaponInstanceRegistry.isWeapon(type)

        if (isWeapon) {
            // Panneau sell
            rightHeaderTv?.text = activity.getString(R.string.cave_inv_sell_header)
            craftingEmptyTv?.visibility      = View.GONE
            craftingRecyclerView?.visibility = View.GONE
            val price = weaponSellPrice(type!!)
            val priceLabel = activity.getString(R.string.cave_inv_sell_price_label)
            val stoneLabel = activity.getString(R.string.cave_inv_sell_ward_stones)
            sellPriceTv?.text = "$priceLabel: $price $stoneLabel"
            sellPanel?.visibility = View.VISIBLE
        } else {
            // Panneau craft normal
            rightHeaderTv?.text = activity.getString(R.string.cave_inv_crafting_header)
            sellPanel?.visibility = View.GONE
            val recipes = if (type == null) emptyList()
                          else CraftRegistry.all().filter { r -> r.ingredients.any { it.first == type } }
            craftingAdapter?.recipes = recipes
            craftingAdapter?.notifyDataSetChanged()
            craftingEmptyTv?.visibility      = if (recipes.isEmpty()) View.VISIBLE else View.GONE
            craftingRecyclerView?.visibility = if (recipes.isEmpty()) View.GONE    else View.VISIBLE
        }
    }

    fun doSell(id: Short) {
        if (!WeaponInstanceRegistry.isWeapon(id)) return
        val price = weaponSellPrice(id)
        // Rémunération en ward stones
        if (price > 0) {
            val WARD_STONE = com.Atom2Universe.app.games.caves.world.WARD_STONE
            renderer.inventory[WARD_STONE] = (renderer.inventory[WARD_STONE] ?: 0) + price
            val existing = invSlots.filterNotNull().toSet()
            if (WARD_STONE !in existing) addNewType(WARD_STONE)
        }
        // Retirer l'arme
        renderer.inventory.remove(id)
        for (i in invSlots.indices) { if (invSlots[i] == id) { invSlots[i] = null; break } }
        for (i in renderer.hotbar.indices) { if (renderer.hotbar[i] == id) renderer.hotbar[i] = null }
        WeaponInstanceRegistry.free(id)
        selectedSlotIdx = -1
        syncHotbar()
        refreshPagedAdapter()
        hud.updateHotbarForInventory()
        updateInfoPanel()
        updateCraftingList()
        activity.saveWorldAsync()
    }

    fun doCraft(recipe: CraftDef) {
        if (!recipe.canCraft(renderer.inventory)) return
        for ((type, need) in recipe.ingredients) {
            val after = (renderer.inventory[type] ?: 0) - need
            if (after <= 0) {
                renderer.inventory.remove(type)
                for (i in invSlots.indices) { if (invSlots[i] == type) { invSlots[i] = null; break } }
            } else renderer.inventory[type] = after
        }
        val outType    = recipe.result
        val outCurrent = renderer.inventory[outType] ?: 0
        renderer.inventory[outType] = outCurrent + recipe.resultCount
        if (outCurrent == 0 && outType !in invSlots.filterNotNull()) addNewType(outType)
        if (selectedSlotIdx in invSlots.indices && invSlots[selectedSlotIdx] == null) selectedSlotIdx = -1
        syncHotbar()
        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
        activity.saveWorldAsync()
    }

    // ── Slot click hotbar réelle (pendant inventaire) ─────────────────────────

    fun onOverlayActiveSlotClick(i: Int) {
        val invIdx = hotbarBase() + i
        when {
            selectedSlotIdx == invIdx -> {
                selectedSlotIdx = -1
                hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
            }
            selectedSlotIdx >= 0 -> {
                swapSlots(selectedSlotIdx, invIdx); selectedSlotIdx = -1
                refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
            }
            invSlots.getOrNull(invIdx) != null -> {
                selectedSlotIdx = invIdx; selectedRecipe = null
                hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
            }
        }
    }

    // ── Navigation manette inventaire ─────────────────────────────────────────

    fun moveInvCursor(dx: Int, dy: Int) {
        val base = hotbarBase()
        val cols = CaveActivity.GRID_COLS
        when (invGpZone) {
            InvGpZone.GRID -> {
                val pageStart = currentPage * pageSize
                val pageEnd   = minOf(pageStart + pageSize, base).coerceAtLeast(pageStart + 1)
                val localIdx  = (invGpCursor - pageStart).coerceIn(0, pageEnd - pageStart - 1)
                val localCol  = localIdx % cols
                val localRow  = localIdx / cols

                when {
                    dy < 0 && localRow == 0 -> {
                        invGpZone   = InvGpZone.HOTBAR
                        invGpCursor = base + localCol.coerceAtMost(CaveActivity.ACTIVE_SIZE - 1)
                    }
                    dy != 0 -> {
                        val newLocal = localIdx + dy * cols
                        invGpCursor = (pageStart + newLocal.coerceIn(0, pageEnd - pageStart - 1))
                    }
                    dx < 0 && localCol == 0 -> changePage(-1)
                    dx > 0 && localCol == cols - 1 -> changePage(1)
                    dx != 0 -> {
                        val clamped = (pageStart + localRow * cols + (localCol + dx)
                            .coerceIn(0, cols - 1)).coerceAtMost(pageEnd - 1)
                        invGpCursor = clamped
                    }
                }
            }
            InvGpZone.HOTBAR -> {
                val rel = invGpCursor - base
                when {
                    dy < 0 && base > 0 -> {
                        invGpZone = InvGpZone.GRID
                        val pageStart = currentPage * pageSize
                        val pageEnd   = minOf(pageStart + pageSize, base).coerceAtLeast(pageStart + 1)
                        val lastRowStart = ((pageEnd - 1) / cols) * cols
                        invGpCursor = (lastRowStart + rel.coerceAtMost(cols - 1)).coerceAtMost(pageEnd - 1)
                    }
                    dx < 0 -> invGpCursor = base + (rel - 1 + CaveActivity.ACTIVE_SIZE) % CaveActivity.ACTIVE_SIZE
                    dx > 0 -> invGpCursor = base + (rel + 1) % CaveActivity.ACTIVE_SIZE
                }
            }
            InvGpZone.CRAFTING -> {
                val count = craftingAdapter?.itemCount ?: 0
                if (count > 0) {
                    invGpCursor = (invGpCursor + dy + count) % count
                    craftingRecyclerView?.scrollToPosition(invGpCursor)
                }
            }
        }
        refreshInvGpCursorUi()
    }

    fun changePage(delta: Int) {
        val newPage = (currentPage + delta).coerceIn(0, pageCount() - 1)
        if (newPage == currentPage) return
        currentPage = newPage
        invPager?.setCurrentItem(currentPage, true)
        if (invGpZone == InvGpZone.GRID) {
            val pageStart = currentPage * pageSize
            val pageEnd   = minOf(pageStart + pageSize, hotbarBase()).coerceAtLeast(pageStart + 1)
            val localCol  = invGpCursor % CaveActivity.GRID_COLS
            invGpCursor   = (pageStart + localCol).coerceAtMost(pageEnd - 1)
        }
        updatePageIndicator()
        refreshInvGpCursorUi()
    }

    fun exitCraftingZone() {
        invGpZone = InvGpZone.HOTBAR
        invGpCursor = hotbarBase()
        selectedRecipe = null
        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
    }

    fun handleInvGamepadMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        val now = System.currentTimeMillis()

        // Stick droit : changement de page
        val rsx = event.getAxisValue(MotionEvent.AXIS_Z)
        val rdx = when { rsx < -0.5f -> -1; rsx > 0.5f -> 1; else -> 0 }
        if (rdx != 0 && now - invGpRightLastMs >= INV_GP_REPEAT_MS) {
            invGpRightLastMs = now
            changePage(rdx)
            return true
        }

        // Stick gauche / croix : déplacement curseur
        if (now - invGpLastMoveMs < INV_GP_REPEAT_MS) return true
        val sx = event.getAxisValue(MotionEvent.AXIS_X); val sy = event.getAxisValue(MotionEvent.AXIS_Y)
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X); val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val rawX = if (sx != 0f) sx else hx; val rawY = if (sy != 0f) sy else hy
        val dx = when { rawX < -0.5f -> -1; rawX > 0.5f -> 1; else -> 0 }
        val dy = when { rawY < -0.5f -> -1; rawY > 0.5f -> 1; else -> 0 }
        if (dx == 0 && dy == 0) return true
        invGpLastMoveMs = now
        moveInvCursor(dx, dy)
        return true
    }

    fun handleInvGamepadKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_Y -> {
            if (invGpZone == InvGpZone.CRAFTING) exitCraftingZone()
            else {
                if (invSlots.getOrNull(invGpCursor) != null) {
                    selectedSlotIdx = invGpCursor
                    invGpZone = InvGpZone.CRAFTING; invGpCursor = 0
                    updateInfoPanel(); updateCraftingList()
                    craftingRecyclerView?.scrollToPosition(0)
                    craftingAdapter?.notifyDataSetChanged()
                }
            }
            true
        }
        KeyEvent.KEYCODE_BUTTON_A -> {
            if (invGpZone == InvGpZone.CRAFTING) {
                craftingAdapter?.recipes?.getOrNull(invGpCursor)?.let { doCraft(it) }
            } else {
                val base = hotbarBase()
                val effectiveIdx = if (invGpZone == InvGpZone.HOTBAR) invGpCursor else invGpCursor
                when {
                    selectedSlotIdx == effectiveIdx -> {
                        selectedSlotIdx = -1
                        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
                    }
                    selectedSlotIdx >= 0 -> {
                        swapSlots(selectedSlotIdx, effectiveIdx); selectedSlotIdx = -1
                        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
                    }
                    invSlots.getOrNull(effectiveIdx) != null -> {
                        selectedSlotIdx = effectiveIdx; selectedRecipe = null
                        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
                    }
                }
            }
            true
        }
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
            when {
                invGpZone == InvGpZone.CRAFTING -> exitCraftingZone()
                selectedSlotIdx >= 0 -> {
                    selectedSlotIdx = -1
                    refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
                }
                else -> closeInventory()
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT  -> { invGpLastMoveMs = 0; moveInvCursor(-1,  0); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { invGpLastMoveMs = 0; moveInvCursor( 1,  0); true }
        KeyEvent.KEYCODE_DPAD_UP    -> { invGpLastMoveMs = 0; moveInvCursor( 0, -1); true }
        KeyEvent.KEYCODE_DPAD_DOWN  -> { invGpLastMoveMs = 0; moveInvCursor( 0,  1); true }
        else -> false
    }

    fun refreshInvGpCursorUi() {
        pagedAdapter?.notifyDataSetChanged()
        hud.updateHotbarForInventory()
        craftingAdapter?.notifyDataSetChanged()
    }

    // ── Adapter pager (une page = RecyclerView grille) ────────────────────────

    inner class PagedInvPagerAdapter(var cellSize: Int) : RecyclerView.Adapter<PagedInvPagerAdapter.PageVH>() {

        inner class PageVH(val rv: RecyclerView) : RecyclerView.ViewHolder(rv)

        fun pageCount() = ((hotbarBase() + pageSize - 1) / pageSize).coerceAtLeast(1)

        override fun getItemCount() = pageCount()

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): PageVH {
            val rv = RecyclerView(parent.context).apply {
                layoutManager = GridLayoutManager(parent.context, CaveActivity.GRID_COLS)
                isNestedScrollingEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return PageVH(rv)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val start = position * pageSize
            val slots = (0 until pageSize).map { invSlots.getOrNull(start + it) }
            holder.rv.adapter = PageSlotAdapter(slots, start, cellSize)
        }
    }

    // ── Adapter d'une page de grille ──────────────────────────────────────────

    inner class PageSlotAdapter(
        val items: List<Short?>,
        val startIdx: Int,
        val cellSize: Int
    ) : RecyclerView.Adapter<PageSlotAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val colorView: View   = v.findViewById(R.id.cave_inv_color)
            val countTv: TextView = v.findViewById(R.id.cave_inv_count)
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cave_inv_block, parent, false)
            view.layoutParams = RecyclerView.LayoutParams(cellSize, cellSize)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val absIdx = startIdx + position
            val type   = items.getOrNull(position)
            val count  = if (type != null) renderer.inventory[type] ?: 0 else 0
            val isSel    = absIdx == selectedSlotIdx
            val isCursor = invGpZone == InvGpZone.GRID && absIdx == invGpCursor
            val dp = activity.resources.displayMetrics.density
            val isWeaponSlot = type != null && WeaponInstanceRegistry.isWeapon(type)
            val rarityStroke = if (isWeaponSlot && !isSel && !isCursor) {
                weaponRarityColor(WeaponInstanceRegistry.get(type!!)?.rarity ?: ItemRarity.COMMON)
            } else null
            holder.itemView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(when { isSel -> 0x44FFDD00.toInt(); isCursor -> 0x3300DDFF.toInt(); type != null -> 0x28FFFFFF.toInt(); else -> 0x12FFFFFF.toInt() })
                val strokeW = if (isSel || isCursor || rarityStroke != null) (2 * dp).toInt() else (1 * dp).toInt()
                val strokeC = when { isSel -> 0xFFFFDD00.toInt(); isCursor -> 0xFF00DDFF.toInt(); rarityStroke != null -> rarityStroke; type != null -> 0x55FFFFFF.toInt(); else -> 0x28FFFFFF.toInt() }
                setStroke(strokeW, strokeC)
                cornerRadius = 5 * dp
            }
            holder.colorView.background = if (type != null) activity.blockDrawable(type, 3f)
                else GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 3 * dp }
            holder.countTv.text = if (type != null && count > 0 && !isWeaponSlot) count.toString() else ""

            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val aIdx = startIdx + pos
                when {
                    selectedSlotIdx == aIdx -> {
                        selectedSlotIdx = -1
                        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
                    }
                    selectedSlotIdx >= 0 -> {
                        swapSlots(selectedSlotIdx, aIdx); selectedSlotIdx = -1
                        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
                    }
                    type != null -> {
                        selectedSlotIdx = aIdx; selectedRecipe = null
                        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
                    }
                }
            }
            holder.itemView.setOnLongClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) { startSlotDrag(it, startIdx + pos); true } else false
            }
            holder.itemView.setOnDragListener(makeSlotDragListener {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { startIdx + it } ?: -1
            })
        }
    }

    // ── Adapter crafting ──────────────────────────────────────────────────────

    inner class CraftingAdapter(var recipes: List<CraftDef>) : RecyclerView.Adapter<CraftingAdapter.VH>() {

        inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val dp = activity.resources.displayMetrics.density
            val ll = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                val m = (6 * dp).toInt(); setPadding(m, m, m, m)
            }
            return VH(ll)
        }

        override fun getItemCount() = recipes.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val recipe   = recipes[position]
            val dp       = activity.resources.displayMetrics.density
            val isSelRec = recipe == selectedRecipe
            val isCursor = invGpZone == InvGpZone.CRAFTING && position == invGpCursor
            holder.root.removeAllViews()
            holder.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(when { isSelRec -> 0x33FFDD00.toInt(); isCursor -> 0x3300DDFF.toInt(); else -> 0x22FFFFFF })
                cornerRadius = 6 * dp
                setStroke(if (isSelRec || isCursor) (2 * dp).toInt() else 1,
                    when { isSelRec -> 0xFFFFDD00.toInt(); isCursor -> 0xFF00DDFF.toInt(); else -> 0x33FFFFFF })
            }
            val row = LinearLayout(holder.root.context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            recipe.ingredients.forEachIndexed { idx, (type, need) ->
                if (idx > 0) row.addView(TextView(holder.root.context).apply {
                    text = "+"; textSize = 12f; setTextColor(0x88FFFFFF.toInt())
                    setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                })
                row.addView(blockLabel(holder.root.context, type, need, dp))
            }
            row.addView(TextView(holder.root.context).apply {
                text = "→"; textSize = 14f; setTextColor(0xAAFFFFFF.toInt())
                setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            })
            row.addView(blockLabel(holder.root.context, recipe.result, recipe.resultCount, dp))
            holder.root.addView(row)
            val canCraft = recipe.canCraft(renderer.inventory)
            holder.root.addView(Button(holder.root.context).apply {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (32 * dp).toInt())
                lp.topMargin = (6 * dp).toInt(); layoutParams = lp
                text = activity.getString(R.string.cave_inv_craft_btn); textSize = 11f
                isEnabled = canCraft; alpha = if (canCraft) 1f else 0.4f
                setBackgroundColor(if (canCraft) 0xFF336633.toInt() else 0x44FFFFFF.toInt())
                setTextColor(Color.WHITE); setPadding(0, 0, 0, 0)
                setOnClickListener { doCraft(recipe) }
            })
            holder.root.setOnClickListener {
                selectedRecipe = if (selectedRecipe == recipe) null else recipe
                notifyDataSetChanged()
                updateInfoPanel()
            }
        }

        private fun blockLabel(ctx: Context, type: Short, count: Int, dp: Float): LinearLayout =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                val sq = (44 * dp).toInt()
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(sq, sq)
                    background = activity.blockDrawable(type, 4f)
                })
                addView(TextView(ctx).apply {
                    text = "×$count"; textSize = 12f; setTextColor(0xCCFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(sq, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
            }
    }
}
