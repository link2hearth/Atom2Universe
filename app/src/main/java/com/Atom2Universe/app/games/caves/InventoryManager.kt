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
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
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
    private lateinit var ui: CaveInventoryPanel
    private var browsingCraft = false
    private var assigningShortcut = false
    private var query = ""
    private var categoryIndex = 0
    private var relatedType: Short? = null
    private var gridIndices = emptyList<Int>()
    private var gridColumns = CaveActivity.GRID_COLS
    private val categoryKeys = listOf("", "terrain", "wood", "stone", "nature", "functional", "cotton")

    // ── Slots ─────────────────────────────────────────────────────────────────
    // Grille + barre pour chaque mode, séparées comme les deux hotbars du renderer
    // (voir [CaveRenderer.hotbar]) — sinon une synchro (craft, drag&drop…) faite
    // pendant que l'autre mode est actif écraserait la mauvaise barre.
    private val combatInvSlots = ArrayList<Short?>()
    private val buildInvSlots  = ArrayList<Short?>()
    val invSlots: ArrayList<Short?> get() =
        if (renderer.hotbarMode == HotbarMode.COMBAT) combatInvSlots else buildInvSlots
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
        ui = CaveInventoryPanel(activity).also { it.populate(invOverlay as FrameLayout) }
        invOverlay.findViewById<View>(R.id.cave_inv_dim_area).setOnClickListener { closeInventory() }
        invOverlay.findViewById<View>(R.id.cave_inv_panel).setOnClickListener { /* consomme */ }
        invOverlay.findViewById<View>(R.id.cave_inv_info_column).setOnClickListener { /* details never dismiss the inventory */ }

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
            ?.also { btn -> btn.setOnClickListener { selectedType()?.let { confirmSell(it) } } }
        invPager             = invOverlay.findViewById(R.id.cave_inv_pager)
        pageIndicatorTv      = invOverlay.findViewById(R.id.cave_inv_page_indicator)
        setupNavigation()

        val ca = CraftingAdapter(emptyList()).also { craftingAdapter = it }
        craftingRecyclerView?.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = ca
        }

        invPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                if (invGpZone == InvGpZone.GRID)
                    invGpCursor = gridIndices.getOrNull(position * pageSize) ?: hotbarBase()
                updatePageIndicator()
                invPager?.post { pagedAdapter?.notifyDataSetChanged() }
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

    private fun setupNavigation() {
        ui.close.setOnClickListener { closeInventory() }
        ui.inventoryTab.setOnClickListener { showLibrary(false) }
        ui.craftTab.setOnClickListener { showLibrary(true) }
        ui.combatTab.setOnClickListener { if (renderer.hotbarMode != HotbarMode.COMBAT) renderer.toggleHotbarMode() }
        ui.buildTab.setOnClickListener { if (renderer.hotbarMode != HotbarMode.BUILD) renderer.toggleHotbarMode() }
        val categories = intArrayOf(R.string.cave_ui_all, R.string.cave_ui_terrain, R.string.cave_ui_wood,
            R.string.cave_ui_stone, R.string.cave_ui_nature, R.string.cave_ui_functional, R.string.cave_ui_cotton)
        ui.category.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
            categories.map { activity.getString(it) })
        ui.category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(CaveUiStyle.TEXT)
                categoryIndex = position; currentPage = 0; refreshPagedAdapter()
            }
        }
        ui.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim().orEmpty(); currentPage = 0
                relatedType = null; refreshPagedAdapter(); updateCraftingList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        ui.craftable.setOnCheckedChangeListener { _, _ -> updateCraftingList() }
        ui.previous.setOnClickListener { changePage(-1) }; ui.next.setOnClickListener { changePage(1) }
        ui.sort.setOnClickListener {
            val sorted = invSlots.take(hotbarBase()).filterNotNull().sortedBy { activity.blockName(it).lowercase() }
            for (i in 0 until hotbarBase()) invSlots[i] = sorted.getOrNull(i)
            selectedSlotIdx = -1; assigningShortcut = false; refreshPagedAdapter(); updateInfoPanel()
        }
        ui.assign.setOnClickListener { assigningShortcut = selectedType() != null; updateActions() }
        ui.cancel.setOnClickListener { assigningShortcut = false; updateActions() }
        ui.remove.setOnClickListener { removeShortcut() }
        ui.related.setOnClickListener {
            relatedType = selectedType(); browsingCraft = true; selectedRecipe = null
            refreshPagedAdapter(); updateCraftingList(); updateInfoPanel()
        }
        ui.craftOne.setOnClickListener { selectedRecipe?.let { doCraft(it) } }
        ui.craftFive.setOnClickListener { selectedRecipe?.let { doCraft(it, 5) } }
    }

    private fun showLibrary(craft: Boolean) {
        browsingCraft = craft; assigningShortcut = false; selectedRecipe = null; relatedType = null
        invGpZone = if (craft) InvGpZone.CRAFTING else InvGpZone.HOTBAR
        invGpCursor = if (craft) 0 else hotbarBase()
        refreshPagedAdapter(); updateCraftingList(); updateInfoPanel()
    }

    private fun updateActions() {
        if (!::ui.isInitialized) return
        val type = selectedType(); val recipe = selectedRecipe
        ui.assign.visibility = if (!browsingCraft && type != null && !assigningShortcut) View.VISIBLE else View.GONE
        ui.remove.visibility = if (!browsingCraft && type != null && selectedSlotIdx >= hotbarBase() && !assigningShortcut) View.VISIBLE else View.GONE
        ui.related.visibility = if (!browsingCraft && type != null && !assigningShortcut) View.VISIBLE else View.GONE
        ui.cancel.visibility = if (assigningShortcut) View.VISIBLE else View.GONE
        ui.craftOne.visibility = if (browsingCraft && recipe != null) View.VISIBLE else View.GONE
        ui.craftFive.visibility = ui.craftOne.visibility
        ui.craftOne.isEnabled = recipe?.canCraft(renderer.inventory) == true
        ui.craftFive.isEnabled = (recipe?.maxCraftable(renderer.inventory) ?: 0) >= 5
        ui.craftOne.alpha = if (ui.craftOne.isEnabled) 1f else .45f
        ui.craftFive.alpha = if (ui.craftFive.isEnabled) 1f else .45f
        ui.status.setText(if (assigningShortcut) R.string.cave_ui_choose_shortcut else if (browsingCraft) R.string.cave_ui_select_recipe else R.string.cave_ui_hint)
        CaveUiStyle.button(ui.inventoryTab, !browsingCraft); CaveUiStyle.button(ui.craftTab, browsingCraft)
        CaveUiStyle.button(ui.combatTab, renderer.hotbarMode == HotbarMode.COMBAT)
        CaveUiStyle.button(ui.buildTab, renderer.hotbarMode == HotbarMode.BUILD)
    }

    private fun selectInventorySlot(index: Int) {
        if (index !in invSlots.indices) return
        if (assigningShortcut && index >= hotbarBase() && selectedType() != null) {
            swapSlots(selectedSlotIdx, index); assigningShortcut = false; selectedSlotIdx = index
        } else {
            assigningShortcut = false
            selectedSlotIdx = if (invSlots[index] != null) index else -1
        }
        selectedRecipe = null
        refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
    }

    private fun removeShortcut() {
        val index = selectedSlotIdx; val type = selectedType() ?: return
        if (index < hotbarBase()) return
        val emptyIndex = (0 until hotbarBase()).firstOrNull { invSlots[it] == null }
        invSlots[index] = null
        if (emptyIndex == null) { val base = hotbarBase(); invSlots.add(base, type); selectedSlotIdx = base }
        else { invSlots[emptyIndex] = type; selectedSlotIdx = emptyIndex }
        syncHotbar(); refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel()
        ui.status.setText(R.string.cave_ui_unassigned); activity.saveWorldAsync()
    }

    private fun confirmSell(id: Short) {
        val offered = WeaponInstanceRegistry.get(id) ?: return
        AlertDialog.Builder(activity).setTitle(R.string.cave_inv_sell_btn)
            .setMessage(activity.getString(R.string.cave_ui_sell_confirm, activity.blockName(id), weaponSellPrice(id)))
            .setNegativeButton(R.string.cave_ui_cancel, null)
            .setPositiveButton(R.string.cave_inv_sell_btn) { _, _ -> if (WeaponInstanceRegistry.get(id) === offered) doSell(id) }.show()
    }

    fun initInvSlots() {
        fun fill(slots: ArrayList<Short?>, hotbar: Array<Short?>, wantCombat: Boolean) {
            slots.clear()
            val hotbarTypes = (0 until CaveActivity.ACTIVE_SIZE).mapNotNull { i ->
                hotbar[i]?.takeIf { (renderer.inventory[it] ?: 0) > 0 }
            }.toSet()
            val gridTypes = renderer.inventory
                .filter { (t, c) -> c > 0 && t !in hotbarTypes && renderer.isCombatItem(t) == wantCombat }
                .keys.sortedBy { it }
            for (t in gridTypes) slots.add(t)
            repeat(CaveActivity.EMPTY_BUFFER) { slots.add(null) }
            for (i in 0 until CaveActivity.ACTIVE_SIZE) {
                val t = hotbar[i]
                slots.add(if (t != null && (renderer.inventory[t] ?: 0) > 0) t else null)
            }
        }
        fill(combatInvSlots, renderer.combatHotbar, wantCombat = true)
        fill(buildInvSlots,  renderer.buildHotbar,  wantCombat = false)
        invSlotsReady = true
        syncHotbar()
    }

    /** Appelé quand le bouton combat/construction bascule : la grille et le panneau
     *  craft/vente doivent refléter la barre désormais active. */
    fun onHotbarModeChanged() {
        assigningShortcut = false
        selectedSlotIdx = -1
        selectedRecipe = null
        syncHotbar()
        refreshPagedAdapter()
        hud.updateHotbarForInventory()
        updateInfoPanel()
        updateCraftingList()
    }

    fun addNewType(type: Short) {
        val base = hotbarBase()
        val hotbarSlot = (base until base + CaveActivity.ACTIVE_SIZE).firstOrNull { invSlots.getOrNull(it) == null }
        if (hotbarSlot != null) { invSlots[hotbarSlot] = type; return }
        val gridSlot = (0 until base).firstOrNull { invSlots[it] == null }
        if (gridSlot != null) { invSlots[gridSlot] = type; return }
        invSlots.add(base, type)
    }

    /** Comme [addNewType], mais range dans la banque correspondant à la catégorie de
     *  l'objet plutôt que dans celle actuellement affichée (utile pour un résultat de
     *  craft, obtenu depuis n'importe quelle banque). */
    private fun addNewTypeByCategory(type: Short) {
        val slots = if (renderer.isCombatItem(type)) combatInvSlots else buildInvSlots
        val base = (slots.size - CaveActivity.ACTIVE_SIZE).coerceAtLeast(0)
        val hotbarSlot = (base until base + CaveActivity.ACTIVE_SIZE).firstOrNull { slots.getOrNull(it) == null }
        if (hotbarSlot != null) { slots[hotbarSlot] = type; return }
        val gridSlot = (0 until base).firstOrNull { slots[it] == null }
        if (gridSlot != null) { slots[gridSlot] = type; return }
        slots.add(base, type)
    }

    fun syncHotbar() {
        for ((slots, bar) in listOf(combatInvSlots to renderer.combatHotbar, buildInvSlots to renderer.buildHotbar)) {
            if (slots.size < CaveActivity.ACTIVE_SIZE) continue
            val base = slots.size - CaveActivity.ACTIVE_SIZE
            for (i in bar.indices) bar[i] = slots.getOrNull(base + i)?.takeIf { (renderer.inventory[it] ?: 0) > 0 }
        }
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

    /** Réconcilie une banque (grille+barre) précise avec l'inventaire global, en ne lui
     *  affectant que les objets de sa catégorie — sinon un objet combat pourrait finir
     *  dans la grille construction juste parce qu'elle était affichée au moment du pickup. */
    private fun reconcileBank(slots: ArrayList<Short?>, hotbar: Array<Short?>, wantCombat: Boolean, inv: Map<Short, Int>) {
        for (i in 0 until CaveActivity.ACTIVE_SIZE) {
            val t = hotbar[i] ?: continue
            if ((inv[t] ?: 0) <= 0) hotbar[i] = null
        }
        if (slots.isEmpty()) return
        for (i in slots.indices) {
            val t = slots[i] ?: continue
            if ((inv[t] ?: 0) <= 0) slots[i] = null
        }
        val existing = slots.filterNotNull().toSet()
        val base = (slots.size - CaveActivity.ACTIVE_SIZE).coerceAtLeast(0)
        for ((type, count) in inv) {
            if (count <= 0 || type in existing || renderer.isCombatItem(type) != wantCombat) continue
            val hotbarIdx = hotbar.indexOfFirst { it == type }
            val directSlot = if (hotbarIdx >= 0) base + hotbarIdx else -1
            when {
                directSlot in slots.indices -> slots[directSlot] = type
                else -> {
                    val hotbarSlot = (base until base + CaveActivity.ACTIVE_SIZE).firstOrNull { slots.getOrNull(it) == null }
                    val gridSlot   = if (hotbarSlot == null) (0 until base).firstOrNull { slots[it] == null } else null
                    when {
                        hotbarSlot != null -> slots[hotbarSlot] = type
                        gridSlot   != null -> slots[gridSlot] = type
                        else               -> slots.add(base, type)
                    }
                }
            }
        }
    }

    fun onInventoryChanged(inv: Map<Short, Int>) {
        if (invSlotsReady) {
            reconcileBank(combatInvSlots, renderer.combatHotbar, wantCombat = true,  inv = inv)
            reconcileBank(buildInvSlots,  renderer.buildHotbar,  wantCombat = false, inv = inv)
            if (selectedSlotIdx >= invSlots.size) selectedSlotIdx = -1
            syncHotbar()
            if (activity.invOverlay.visibility == View.VISIBLE) {
                refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
            }
        } else {
            for (hb in arrayOf(renderer.combatHotbar, renderer.buildHotbar)) {
                for (i in 0 until CaveActivity.ACTIVE_SIZE) {
                    val t = hb[i] ?: continue
                    if ((inv[t] ?: 0) <= 0) hb[i] = null
                }
            }
            renderer.hotbarCallback?.invoke(renderer.hotbar.copyOf(), renderer.selectedSlot)
        }
    }

    // ── Inventaire open/close ─────────────────────────────────────────────────

    fun openInventory() {
        assigningShortcut = false; selectedSlotIdx = -1; selectedRecipe = null
        activity.releaseGameInputs()
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
                    gridColumns = (pager.width / CaveUiStyle.dp(activity, 64)).coerceIn(4, 10)
                    val cellSize = pager.width / gridColumns
                    val rows = if (cellSize > 0) (pager.height / cellSize).coerceAtLeast(1) else 5
                    pageSize = gridColumns * rows
                }
                val cellSize = if (pager.width > 0) pager.width / gridColumns
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
        assigningShortcut = false; dragSourceIdx = -1
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(ui.search.windowToken, 0)
        ui.search.clearFocus()
        selectedSlotIdx = -1
        selectedRecipe  = null
        invGpZone       = InvGpZone.HOTBAR
        invGpCursor     = 0
        activity.invOverlay.visibility = View.GONE
        hud.updateHotbarUI(renderer.hotbar, renderer.selectedSlot)
    }

    fun refreshPagedAdapter() {
        gridIndices = (0 until hotbarBase()).filter { index ->
            val type = invSlots[index] ?: return@filter false
            (renderer.inventory[type] ?: 0) > 0 && activity.blockName(type).contains(query, ignoreCase = true) &&
                (categoryIndex == 0 || renderer.hotbarMode == HotbarMode.COMBAT || BlockRegistry.get(type)?.creativeTab == categoryKeys[categoryIndex])
        }
        currentPage = currentPage.coerceIn(0, pageCount() - 1)
        pagedAdapter?.notifyDataSetChanged()
        invPager?.setCurrentItem(currentPage, false)
        if (::ui.isInitialized) {
            ui.pager.visibility = if (browsingCraft) View.GONE else View.VISIBLE
            ui.footer.visibility = if (browsingCraft) View.GONE else View.VISIBLE
            ui.category.visibility = if (!browsingCraft && renderer.hotbarMode == HotbarMode.BUILD) View.VISIBLE else View.GONE
            ui.craftable.visibility = if (browsingCraft) View.VISIBLE else View.GONE
            if (!browsingCraft) ui.empty.visibility = if (gridIndices.isEmpty()) View.VISIBLE else View.GONE
            updateActions()
        }
        updatePageIndicator()
    }

    private fun pageCount() = ((gridIndices.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    private fun updatePageIndicator() {
        pageIndicatorTv?.text = activity.getString(R.string.cave_ui_page, currentPage + 1, pageCount())
        if (::ui.isInitialized) { ui.previous.isEnabled = currentPage > 0; ui.next.isEnabled = currentPage + 1 < pageCount() }
    }

    // ── Panneau info ──────────────────────────────────────────────────────────

    fun updateInfoPanel() {
        val recipe = selectedRecipe
        val dp = activity.resources.displayMetrics.density
        updateActions()
        if (recipe != null) {
            val weaponDefId = recipe.resultItemId
            if (weaponDefId != null) {
                infoSpriteView?.background = weaponSpriteDrawable(weaponDefId, 6f)
                infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                infoNameTv?.text  = activity.weaponName(weaponDefId)
                infoCountTv?.text = "×1"
            } else {
                infoSpriteView?.background = activity.blockDrawable(recipe.result, 6f)
                infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                infoNameTv?.text  = activity.blockName(recipe.result)
                infoCountTv?.text = "×${recipe.resultCount}"
            }
            infoIngredientsTv?.text = recipe.ingredients.joinToString("\n") { (t, n) -> activity.getString(R.string.cave_ui_ingredient, activity.blockName(t), renderer.inventory[t] ?: 0, n) } + "\n\n" + activity.getString(R.string.cave_ui_available_batches, recipe.maxCraftable(renderer.inventory))
            infoDivider?.visibility       = View.VISIBLE
            infoIngredientsTv?.visibility = View.VISIBLE
        } else {
            val type = if (browsingCraft) null else selectedType()
            val isWeapon = type != null && WeaponInstanceRegistry.isWeapon(type)
            if (isWeapon) {
                val instance = WeaponInstanceRegistry.get(type!!)
                val def = instance?.let { com.Atom2Universe.app.games.caves.node.ItemRegistry.get(it.defId) }
                infoSpriteView?.background = activity.blockDrawable(type, 6f)
                val rarityColor = weaponRarityColor(instance?.rarity ?: ItemRarity.COMMON)
                val rarityLabel = instance?.rarity?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "?"
                val baseName = def?.id?.let { activity.weaponName(it) } ?: "?"
                infoNameTv?.setTextColor(rarityColor)
                infoNameTv?.text  = "[$rarityLabel]\n$baseName"
                infoCountTv?.text = ""
                infoDivider?.visibility = View.VISIBLE
                val dmg   = instance?.rolledDamage ?: 0
                val speed = def?.attackSpeedMs ?: 0
                val extra = instance?.rolledStats?.entries?.joinToString("\n") { (k, v) ->
                    val label = affixLabel(k)
                    val suffix = affixSuffix(k)
                    "$label: $v$suffix"
                } ?: ""
                infoIngredientsTv?.text = buildString {
                    append(activity.getString(R.string.cave_ui_damage, dmg, speed))
                    if (extra.isNotEmpty()) { append("\n"); append(extra) }
                    val description=activity.resources.getIdentifier("cave_weapon_style_${def?.id}","string",activity.packageName)
                    if(description!=0) { append("\n");append(activity.getString(description)) }
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
                    infoNameTv?.text  = activity.getString(if (browsingCraft) R.string.cave_ui_select_recipe else R.string.cave_ui_select_item)
                    infoCountTv?.text = ""
                } else {
                    infoSpriteView?.background = activity.blockDrawable(type, 6f)
                    infoNameTv?.setTextColor(0xFFFFFFFF.toInt())
                    infoNameTv?.text  = activity.blockName(type)
                    infoCountTv?.text = activity.getString(R.string.cave_ui_owned, renderer.inventory[type] ?: 0)
                }
            }
        }
    }

    /** Icône d'un résultat de recette d'arme (pas encore d'instance rollée, juste l'aperçu). */
    private fun weaponSpriteDrawable(defId: String, cornerDp: Float): android.graphics.drawable.Drawable {
        val def = com.Atom2Universe.app.games.caves.node.ItemRegistry.get(defId)
        return com.Atom2Universe.app.games.caves.render.WeaponIconDrawable(activity.assets,
            def?.weaponType ?: def?.sprite ?: defId,ItemRarity.COMMON)
    }

    private fun affixLabel(key: String): String = when (key) {
        "crit_chance"   -> activity.getString(R.string.cave_affix_crit_chance)
        "crit_dmg"      -> activity.getString(R.string.cave_affix_crit_dmg)
        "attack_speed"  -> activity.getString(R.string.cave_affix_attack_speed)
        "life_steal"    -> activity.getString(R.string.cave_affix_life_steal)
        "bleed_chance"    -> activity.getString(R.string.cave_affix_bleed_chance)
        "electric_chance" -> activity.getString(R.string.cave_affix_electric_chance)
        "freeze_chance"   -> activity.getString(R.string.cave_affix_freeze_chance)
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
        ItemRarity.RARE      -> CaveUiStyle.ACCENT
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
        sellPanel?.visibility = if (!browsingCraft && type != null && WeaponInstanceRegistry.get(type) != null) View.VISIBLE else View.GONE
        if (type != null) sellPriceTv?.text = activity.getString(R.string.cave_inv_sell_price_label) + ": " + weaponSellPrice(type) + " " + activity.getString(R.string.cave_inv_sell_ward_stones)
        rightHeaderTv?.setText(if (browsingCraft) R.string.cave_ui_workshop else R.string.cave_ui_details)
        val recipes = CraftRegistry.all().filter { r ->
            (!ui.craftable.isChecked || r.canCraft(renderer.inventory)) &&
                (relatedType == null || r.ingredients.any { it.first == relatedType }) &&
                (recipeName(r).contains(query, true) || r.ingredients.any { activity.blockName(it.first).contains(query, true) })
        }.sortedWith(compareByDescending<CraftDef> { it.canCraft(renderer.inventory) }.thenBy { recipeName(it) })
        craftingAdapter?.recipes = recipes
        craftingAdapter?.notifyDataSetChanged()
        craftingRecyclerView?.visibility = if (browsingCraft && recipes.isNotEmpty()) View.VISIBLE else View.GONE
        craftingEmptyTv?.visibility = if (browsingCraft && recipes.isEmpty() || !browsingCraft && gridIndices.isEmpty()) View.VISIBLE else View.GONE
        updateActions()
    }

    private fun recipeName(recipe: CraftDef) = recipe.resultItemId?.let { activity.weaponName(it) } ?: activity.blockName(recipe.result)

    fun doSell(id: Short) {
        if (WeaponInstanceRegistry.get(id) == null || (renderer.inventory[id] ?: 0) <= 0) return
        val price = weaponSellPrice(id)
        // Rémunération en ward stones
        if (price > 0) {
            val WARD_STONE = com.Atom2Universe.app.games.caves.world.WARD_STONE
            renderer.inventory[WARD_STONE] = (renderer.inventory[WARD_STONE] ?: 0) + price
            val existing = invSlots.filterNotNull().toSet()
            if (WARD_STONE !in combatInvSlots && WARD_STONE !in buildInvSlots) addNewTypeByCategory(WARD_STONE)
        }
        // Retirer l'arme
        renderer.inventory.remove(id)
        for (slots in listOf(combatInvSlots, buildInvSlots)) for (i in slots.indices) { if (slots[i] == id) slots[i] = null }
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

    fun doCraft(recipe: CraftDef, batches: Int = 1) {
        if (batches !in 1..5 || recipe.maxCraftable(renderer.inventory) < batches) {
            ui.status.setText(R.string.cave_ui_missing); return
        }
        val allocated = mutableListOf<Short>()
        val weaponDefId = recipe.resultItemId
        if (weaponDefId != null) {
            val prepared = runCatching {
                repeat(batches) {
                    val instance = com.Atom2Universe.app.games.caves.node.ItemRegistry.rollInstance(weaponDefId, kotlin.random.Random.Default)
                        ?: error("Unknown craft output")
                    allocated += WeaponInstanceRegistry.allocate(instance)
                }
            }
            if (prepared.isFailure) {
                allocated.forEach { WeaponInstanceRegistry.free(it) }
                ui.status.setText(R.string.cave_ui_craft_failed); return
            }
        } else if ((renderer.inventory[recipe.result] ?: 0).toLong() + recipe.resultCount.toLong() * batches > Int.MAX_VALUE) {
            ui.status.setText(R.string.cave_ui_craft_failed); return
        }
        for ((type, need) in recipe.requiredIngredients) {
            val after = (renderer.inventory[type] ?: 0) - need * batches
            if (after <= 0) {
                renderer.inventory.remove(type)
                for (slots in listOf(combatInvSlots, buildInvSlots)) for (i in slots.indices)
                    if (slots[i] == type) slots[i] = null
            } else renderer.inventory[type] = after
        }
        if (weaponDefId != null) for (id in allocated) {
            renderer.inventory[id] = 1; addNewTypeByCategory(id)
        } else {
            val out = recipe.result
            renderer.inventory[out] = (renderer.inventory[out] ?: 0) + recipe.resultCount * batches
            if (out !in combatInvSlots && out !in buildInvSlots) addNewTypeByCategory(out)
        }
        selectedSlotIdx = -1
        syncHotbar(); refreshPagedAdapter(); hud.updateHotbarForInventory(); updateInfoPanel(); updateCraftingList()
        ui.status.text = activity.getString(R.string.cave_ui_crafted, recipeName(recipe), if (weaponDefId != null) batches else recipe.resultCount * batches)
        activity.saveWorldAsync()
    }

    // ── Slot click hotbar réelle (pendant inventaire) ─────────────────────────

    fun onOverlayActiveSlotClick(i: Int) {
        selectInventorySlot(hotbarBase() + i)
    }

    // ── Navigation manette inventaire ─────────────────────────────────────────

    fun moveInvCursor(dx: Int, dy: Int) {
        val base = hotbarBase()
        when (invGpZone) {
            InvGpZone.GRID -> {
                val pos = gridIndices.indexOf(invGpCursor).coerceAtLeast(0)
                val next = pos + dx + dy * gridColumns
                if (next < 0 || next >= gridIndices.size) {
                    invGpZone = InvGpZone.HOTBAR; invGpCursor = base
                } else {
                    invGpCursor = gridIndices[next]; currentPage = next / pageSize
                    invPager?.setCurrentItem(currentPage, false)
                }
            }
            InvGpZone.HOTBAR -> {
                if (dy < 0 && gridIndices.isNotEmpty() && !browsingCraft) {
                    invGpZone = InvGpZone.GRID
                    invGpCursor = gridIndices[minOf((currentPage + 1) * pageSize, gridIndices.size) - 1]
                } else invGpCursor = base + Math.floorMod(invGpCursor - base + dx, CaveActivity.ACTIVE_SIZE)
            }
            InvGpZone.CRAFTING -> {
                val count = craftingAdapter?.itemCount ?: 0
                if (count > 0) { invGpCursor = Math.floorMod(invGpCursor + dy, count); craftingRecyclerView?.scrollToPosition(invGpCursor) }
            }
        }
        updatePageIndicator(); refreshInvGpCursorUi()
    }

    fun changePage(delta: Int) {
        currentPage = (currentPage + delta).coerceIn(0, pageCount() - 1)
        invPager?.setCurrentItem(currentPage, true)
        if (invGpZone == InvGpZone.GRID) invGpCursor = gridIndices.getOrNull(currentPage * pageSize) ?: hotbarBase()
        updatePageIndicator(); refreshInvGpCursorUi()
    }

    fun exitCraftingZone() { showLibrary(false) }

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
        KeyEvent.KEYCODE_BUTTON_Y -> { showLibrary(!browsingCraft); true }
        KeyEvent.KEYCODE_BUTTON_A -> {
            if (invGpZone == InvGpZone.CRAFTING) {
                selectedRecipe = craftingAdapter?.recipes?.getOrNull(invGpCursor)
                craftingAdapter?.notifyDataSetChanged(); updateInfoPanel()
            } else selectInventorySlot(invGpCursor)
            true
        }
        KeyEvent.KEYCODE_BUTTON_X -> {
            if (browsingCraft) selectedRecipe?.let { doCraft(it) }
            else if (selectedType() != null) { assigningShortcut = true; invGpZone = InvGpZone.HOTBAR; invGpCursor = hotbarBase(); updateActions() }
            true
        }
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
            when { assigningShortcut -> { assigningShortcut = false; updateActions() }
                browsingCraft -> showLibrary(false)
                else -> closeInventory() }
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> { moveInvCursor(-1, 0); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { moveInvCursor(1, 0); true }
        KeyEvent.KEYCODE_DPAD_UP -> { moveInvCursor(0, -1); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { moveInvCursor(0, 1); true }
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

        fun pageCount() = ((gridIndices.size + pageSize - 1) / pageSize).coerceAtLeast(1)

        override fun getItemCount() = pageCount()

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): PageVH {
            val rv = RecyclerView(parent.context).apply {
                layoutManager = GridLayoutManager(parent.context, gridColumns)
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
            val indices = gridIndices.drop(start).take(pageSize)
            holder.rv.adapter = PageSlotAdapter(indices.map { invSlots.getOrNull(it) }, indices, cellSize)
        }
    }

    // ── Adapter d'une page de grille ──────────────────────────────────────────

    inner class PageSlotAdapter(
        val items: List<Short?>,
        val indices: List<Int>,
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
            view.layoutParams = RecyclerView.LayoutParams(cellSize - CaveUiStyle.dp(activity, 6), cellSize - CaveUiStyle.dp(activity, 6)).also { it.setMargins(3, 3, 3, 3) }
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val absIdx = indices[position]
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
                setColor(when { isSel -> CaveUiStyle.SELECTED; isCursor -> 0x66567570; type != null -> 0x28FFFFFF.toInt(); else -> 0x12FFFFFF.toInt() })
                val strokeW = if (isSel || isCursor || rarityStroke != null) (2 * dp).toInt() else (1 * dp).toInt()
                val strokeC = when { isSel -> CaveUiStyle.ACCENT; isCursor -> 0xFFB0D5D3.toInt(); rarityStroke != null -> rarityStroke; type != null -> 0x55FFFFFF.toInt(); else -> 0x28FFFFFF.toInt() }
                setStroke(strokeW, strokeC)
                cornerRadius = 5 * dp
            }
            holder.colorView.background = if (type != null) activity.blockDrawable(type, 3f)
                else GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 3 * dp }
            holder.countTv.text = if (type != null && count > 0 && !isWeaponSlot) count.toString() else ""

            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                selectInventorySlot(indices[pos])
            }
            holder.itemView.setOnLongClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) { startSlotDrag(it, indices[pos]); true } else false
            }
            holder.itemView.setOnDragListener(makeSlotDragListener {
                holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { indices.getOrNull(it) } ?: -1
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
                val m = (10 * dp).toInt(); setPadding(m, m, m, m)
                (layoutParams as RecyclerView.LayoutParams).bottomMargin = (8 * dp).toInt()
            }
            return VH(ll)
        }

        override fun getItemCount() = recipes.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val recipe = recipes[position]
            val chosen = recipe == selectedRecipe
            val cursor = invGpZone == InvGpZone.CRAFTING && position == invGpCursor
            holder.root.removeAllViews()
            holder.root.background = CaveUiStyle.panel(activity, if (chosen) CaveUiStyle.SELECTED else 0x66334A3D,
                if (chosen || cursor) CaveUiStyle.ACCENT else CaveUiStyle.BORDER, chosen || cursor)
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(View(activity).apply {
                background = recipe.resultItemId?.let { weaponSpriteDrawable(it, 6f) } ?: activity.blockDrawable(recipe.result, 6f)
            }, LinearLayout.LayoutParams(CaveUiStyle.dp(activity, 48), CaveUiStyle.dp(activity, 48)))
            row.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(CaveUiStyle.dp(activity, 12), 0, 0, 0)
                addView(TextView(activity).apply { text = recipeName(recipe); textSize = 15f; setTextColor(CaveUiStyle.TEXT); setTypeface(typeface, 1) })
                addView(TextView(activity).apply {
                    text = activity.getString(if (recipe.canCraft(renderer.inventory)) R.string.cave_ui_ready else R.string.cave_ui_missing)
                    textSize = 12f; setTextColor(if (recipe.canCraft(renderer.inventory)) CaveUiStyle.ACCENT else CaveUiStyle.MUTED)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            holder.root.addView(row)
            holder.root.contentDescription = recipeName(recipe)
            holder.root.isFocusable = true
            holder.root.setOnClickListener { selectedRecipe = recipe; notifyDataSetChanged(); updateInfoPanel() }
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

        private fun weaponResultLabel(ctx: Context, defId: String, dp: Float): LinearLayout =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                val sq = (44 * dp).toInt()
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(sq, sq)
                    background = weaponSpriteDrawable(defId, 4f)
                })
                addView(TextView(ctx).apply {
                    text = "×1"; textSize = 12f; setTextColor(0xCCFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(sq, LinearLayout.LayoutParams.WRAP_CONTENT)
                })
            }
    }
}
